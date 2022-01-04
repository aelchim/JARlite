import groovy.io.FileType
import jdk.internal.org.objectweb.asm.ClassReader
import jdk.internal.org.objectweb.asm.ClassVisitor
import jdk.internal.org.objectweb.asm.commons.Remapper
import jdk.internal.org.objectweb.asm.commons.RemappingClassAdapter
import jdk.internal.org.objectweb.asm.tree.ClassNode

import java.nio.file.Files
import java.nio.file.Paths

/**
 * Conceptually:
 * 1. First pass will get a list of usages of library in your project
 * 2. Then we need to pull up those files from the jar lib
 * 3. then for each of those files we need to recursively get the imports from those files with the same group id
 */
class LiteJars {

    static void main(String[] args) {
        // given root of your project
        def projectLocation = "" //args[0]
        // given group id of the lib you want to make a lite version of
        def groupId = "" //args[1]
        //given the source code of the lib you want to make a lite version of
        def jarSource = "" // args [2]
        //loads all project files
        def projectFiles = findFiles(projectLocation)
        def jarFiles = findFiles(jarSource)
        def artifactId = new StringBuilder()
        def version = new StringBuilder()
        //finds occurrences of imported library by group id
        def libFilesNeeded = new HashSet()
        findLibImportsForProject(projectFiles, groupId, libFilesNeeded)
        findNecessaryClasses(groupId, libFilesNeeded)
        createLiteJar(libFilesNeeded, jarFiles, jarSource)
        setupPOMVariables(jarSource, groupId, artifactId, version)
        generatePOMXML(jarSource, groupId, artifactId, version)
    }


    static def findFiles(sourceLocation) {
        def list = []
        def dir = new File(sourceLocation)
        dir.eachFileRecurse(FileType.FILES) { file ->
            list << file
        }
        return list
    }

    static def findLibImportsForProject(files, groupId, libFilesNeeded) {
        files.each {
            if (it.path.contains(".java")) {
                new File(it.path).eachLine { line ->
                    if (line.contains(groupId) && !line.startsWith("package") && !line.contains("@link") && !line.contains("@see")) {
                        //add to occurrences, if successful
                        libFilesNeeded.add(line.substring(7, line.length() - 1))
                    }
                }
            }
        }
    }

    static void findNecessaryClasses(groupId, libFilesNeeded) {
        def classes = new HashSet()
        classes.addAll(libFilesNeeded)
        classes.each { val ->
            try {
                classes = getClassesUsedBy(val, groupId)
                for (final Class<?> cls : classes) {
                    def name = cls.getName()
                    if (name.contains("\$")) {
                        name = cls.getName().substring(0, cls.getName().indexOf('$'))
                    }
                    if (libFilesNeeded.add(name)) {
                        findNecessaryClasses(groupId, libFilesNeeded)
                    }
                }
            } catch (IOException e) {
                println "log then continue: " + e + " : " + val + " ; To fix this import the necessary dependency in the pom.xml"
            }
        }
    }

    static void createLiteJar(occurrences, jarFiles, jarSource) {
        jarFiles.each { lib ->
            occurrences.each { val ->
                try {
                    def valPath = val.replaceAll("[.]", "/")
                    if (lib.path.contains(valPath + ".java")) {
                        def source = Paths.get(lib.path)
                        def targetPath = lib.path.replace(jarSource, jarSource.substring(0, jarSource.length() - 1) + "-lite/")
                        def target = Paths.get(targetPath)
                        new File(targetPath.substring(0, targetPath.lastIndexOf('/'))).mkdirs()
                        //delete before copying
                        new File(targetPath).delete()
                        Files.copy(source, target)
                    }
                } catch (MissingMethodException e) {
                    println e
                }
            }
        }
    }

    static void setupPOMVariables(jarSource, groupId, artifactId, version) {

        def setupPomVariables = true
        // get version and appId from existing pom.xml
        new File(jarSource + "/pom.xml").eachLine { line ->
            if (setupPomVariables && line.contains("<artifactId>")) {
                //add to occurrences, if successful
                artifactId.append(line.replace("<artifactId>", "").replace("</artifactId>", "").trim())
            } else if (setupPomVariables && line.contains("<version>")) {
                //add to occurrences, if successful
                version.append(line.replace("<version>", "").replace("</version>", "").trim())
                version.append("-lite")
            }

            if (line.contains("<parent>")) {
                setupPomVariables = false
            }
        }
    }

    static void generatePOMXML(jarSource, groupId, artifactId, version) {
        def folder = new File(jarSource.substring(0, jarSource.length() - 1) + "-lite/")
        def generatedPom = new File("src/main/resources/module_pom.xml")
        def uniqueSources = new HashSet()

        folder.eachFileRecurse FileType.FILES, { file ->
            // do nothing if the file ends with a .txt extension
            if (file.path.contains("/src/")) {
                uniqueSources.add(file.path.substring(0, file.path.indexOf("/src/")))
            }
        }

        uniqueSources.each { path ->

            new File(path + "/pom.xml").delete()
            def targetPOM = new File(path + "/pom.xml")

            //write to new pom.xml
            targetPOM.withWriter { writer ->
                generatedPom.withReader { reader ->
                    def line
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("\${groupId}")) {
                            writer << line.replace("\${groupId}", groupId) << "\n"
                        } else if (line.contains("\${artifactId}")) {
                            writer << line.replace("\${artifactId}", artifactId.toString()) << "\n"
                        } else if (line.contains("\${version}")) {
                            writer << line.replace("\${version}", version.toString()) << "\n"
                        } else if (line.contains("\${moduleId}")) {
                            writer << line.replace("\${moduleId}", path.substring(path.lastIndexOf("/") + 1)) << "\n"
                        } else if (line.contains("</modules>")) {
                            writer << "\t\t<module>" << artifactId.toString() << "</module>\n" << "\t</modules>"
                        } else {
                            writer << "${line}\n"
                        }
                    }
                }
            }
        }

        generatedPom = new File("src/main/resources/pom.xml")
        new File(jarSource.substring(0, jarSource.length() - 1) + "-lite/pom.xml").delete()
        def targetPOM = new File(jarSource.substring(0, jarSource.length() - 1) + "-lite" + "/pom.xml")

        targetPOM.withWriterAppend { writer ->
            generatedPom.withReader { reader ->
                def line
                while ((line = reader.readLine()) != null) {
                    if (line.contains("\${groupId}")) {
                        writer << line.replace("\${groupId}", groupId) << "\n"
                    } else if (line.contains("\${artifactId}")) {
                        writer << line.replace("\${artifactId}", artifactId.toString()) << "\n"
                    } else if (line.contains("\${version}")) {
                        writer << line.replace("\${version}", version.toString()) << "\n"
                    } else if (line.contains("<modules>")) {
                        writer.append("\t<modules>\n")
                        uniqueSources.each { path ->
                            writer.append("\t\t<module>" + path.substring(path.lastIndexOf("/") + 1) + "</module>\n")
                        }
                    } else {
                        writer << "${line}\n"
                    }
                }
            }
        }
    }

    static Set<Class<?>> getClassesUsedBy(
            final String name,   // class name
            final String prefix  // common prefix for all classes
            // that will be retrieved
    ) throws IOException {
        final ClassReader reader = new ClassReader(name)
        final Set<Class<?>> classes =
                new TreeSet<Class<?>>(new Comparator<Class<?>>() {
                    @Override
                    public int compare(final Class<?> o1, final Class<?> o2) {
                        return o1.getName().compareTo(o2.getName())
                    }
                })
        final Remapper remapper = new Collector(classes, prefix)
        final ClassVisitor inner = new ClassNode()
        final RemappingClassAdapter visitor =
                new RemappingClassAdapter(inner, remapper)
        reader.accept(visitor, 8)
        return classes
    }
}