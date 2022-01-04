# JARlite

JARlite creates a lighter version of the JAR you are using by analyzing your project and given a library. 
This lighter version contains only the files from the jars that you need.

##Install
Clone the repository and run mvn clean install

##How to use
We recommend that this is ran for 1 jar/lib at a time.

1. In the root pom.xml add the dependency that you want to make the lite version of
2. In the LiteJars file there are 3 variables that need to be set
   - projectLocation - set the directory of your project
   - groupId - set the groupId value of the library
   - jarSource - source code of the library
3. Run main method    