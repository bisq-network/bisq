mvn clean package -DskipTests -Dmaven.javadoc.skip=true
java -jar gui/target/shaded.jar
