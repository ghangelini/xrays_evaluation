# ====== Etapa de Construccin (Build) ======
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copia el archivo pom.xml y descarga las dependencias (mejora el cach de docker)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copia el cdigo fuente y compila el proyecto (.jar)
COPY src ./src
RUN mvn clean package -DskipTests

# ====== Etapa de Ejecucin (Run) ======
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# ONNX Runtime en Linux (ubuntu/debian va jammy) suele requerir libgomp1 para operaciones tensionales 
RUN apt-get update && apt-get install -y libgomp1 && rm -rf /var/lib/apt/lists/*

# Copia el ejecutable desde la etapa de construccin
COPY --from=build /app/target/xrays_evaluation-1.0.0.jar app.jar

# Copia de forma explcita la carpeta "models" ya que la ruta en Java apuntaba relativamente: "models/xrays..."
COPY models/ ./models/

# Render / Railway suelen inyectar el puerto en la variable de entorno $PORT. 
# Exponemos el puerto estndar por precaucin, y al ejecutar seteamos spring port = PORT o 8080.
EXPOSE 8080

# Ejecuta la aplicacin
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]
