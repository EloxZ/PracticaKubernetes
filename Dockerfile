FROM openjdk:8
WORKDIR /
ADD java_spark.jar java_spark.jar
CMD java -jar java_spark.jar
