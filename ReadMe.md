
# Create Docker Image of your Spring Boot app.
# Run your Spring Boot application in Docker container and connect to Postgres in Docker.

- This project demonstrates starting a Spring Boot app in a docker container.  
- It uses Docker compose to run the Spring Boot app connected to Postgres DB with all of these running in their own separate containers.

# Steps to run the project
Step 0: Make sure docker is installed and running.
Step 1: Build the project to create the .jar file (`./mvnw clean package -DskipTests`) (If needed, `chmod +x mvnw`)  
Step 2: Set the environment variable for Postgres DB password like this  
`export POSTGRES_DB_PWD=admin`   
Step 3: Run docker compose up:  
`docker compose -f ./docker-compose.yaml up`  
Step 4: Use tool like Postman to send request to the end-point for creating product record and viewing product record:  
http://localhost:8081/product-service/addProduct  
http://localhost:8081/product-service/getAllProducts  
http://localhost:8081/product-service/getProduct/1

## Some important commands
- Get inside a Docker container's terminal 
`docker exec -it <container_name or id> /bin/bash`
- `psql -U postgres`: To start psql for Postgres once inside the container.
- CLI commands for Postgres: `\l` to see the databases, `\c` to connect to a specific database, `\dt` to see the tables
- Up docker containers/services  
  `docker compose up -d` (use without -d if you need to see the logs)  
  `docker compose -f ./docker-compose.yaml up`
- Down the docker containers/services  
  `docker compose down`
- Build docker image of your Spring boot app using Dockerfile  
  `docker build -t docker-springboot:1 .`
- Run your newly created image in docker  
  `docker run -p 8081:8081 docker-springboot:1`
