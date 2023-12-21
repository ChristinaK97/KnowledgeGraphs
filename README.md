## ENCRYPT Knowledge Graphs

### Knowledge Graphs Component Modules
* __KnowledgeGraphsJava__: Main codebase implemented with Java. Its Dockerfile is used to build the ```knowledge-graphs-main``` image.
* __KnowledgeGraphsPython__: Encompasses two following python modules Its Dockerfile is used to build the ```knowledge-graphs-python``` image.
  * AAExpansion: Medical Abbreviation and Acronym Expansion preprocessing module
  * DeepOnto: Forked BERTMap codebase
* __PythonEnvironment__: Contains the requirements.txt for the python projects. Its Dockerfile is used to build a base image for the ```knowledge-graphs-python``` image,
encompassing all the requirements of the two python projects.

Only the ```knowledge-graphs-main``` and ```knowledge-graphs-python``` images are necessary to deploy the KGs component, as well as the official GraphDB image hosted at DockerHub.


### Knowledge Graphs External Data
These docker images are accompanied by a data directory containing essential external resources. These include: 

- BERT-based PyTorch models fine-tuned on training data from the Fintech and Health domains, 
- domain ontologies selected for each dataset, 
- configuration files.

These resources are packaged within a tar file. This file can be found at the ENCRYPT sharepoint: ```Documents/Work/WP4/T4.2 Knowledge Graphs/kg-data-backup.tar``` <br>
How to use?

- Option 1: [Bind mount method] Extract the contents and mount the resulting local directory ".KnowledgeGraphsData" to the KGs containers. <br>
    - Step 1: Extract tar
        - Linux: 
          > docker run --rm --mount type=bind,source=$(pwd),target=/data --name temp_container busybox sh -c "mkdir -p /data/.KnowledgeGraphsData && cd /data && tar xvf kg-data-backup.tar -C /data/.KnowledgeGraphsData --strip-components=2"
        - Windows:
          > docker run --rm --mount type=bind,source=%cd%,target=/data --name temp_container busybox sh -c "mkdir -p /data/.KnowledgeGraphsData && cd /data && tar xvf kg-data-backup.tar -C /data/.KnowledgeGraphsData --strip-components=2"
    - Step 2: Place the resulting directory ".KnowledgeGraphsData" in the same directory as the docker-compose.yml file. This path is specified by the ```KNOWLEDGE_GRAPHS_DATA_PATH``` value in the .env file. 
    - Step 3: On both the ```knowledge-graphs-main``` and ```knowledge-graphs-python``` services in the docker-compose specify the following bind mount: <br>
      ```volumes:```<br>```- ${KNOWLEDGE_GRAPHS_DATA_PATH}:/KnowledgeGraphsApp/data```

      
- Option 2: [Volume method] Use a new named external volume
    - Step 1: Migrate the data of the tar file to a new external volume named ```encrypt-knowledge-graphs-data```
        - Linux:
          > docker run --rm -v encrypt-knowledge-graphs-data:/data --mount type=bind,source=$(pwd),target=/backup_mount --name temp_container busybox sh -c "tar xvf /backup_mount/kg-data-backup.tar -C /data --strip-components=2"
        - Windows:
          > docker run --rm -v encrypt-knowledge-graphs-data:/data --mount type=bind,source=%cd%,target=/backup_mount --name temp_container busybox sh -c "tar xvf /backup_mount/kg-data-backup.tar -C /data --strip-components=2"
    - Step 2: On the docker-compose file, define the following volume:<br>
      ```volumes:```<br>```encrypt-knowledge-graphs-data:```<br>```name: encrypt-knowledge-graphs-data```<br>```external: true```
    - Step 3: On both the ```knowledge-graphs-main``` and ```knowledge-graphs-python``` services in the docker-compose add the following volume: <br>
      ```volumes:```<br>```- encrypt-knowledge-graphs-data:/KnowledgeGraphsApp/data```

This shared directory also serves as the output space for the containers, functioning as persistent storage to ensure the preservation and accessibility of its contents across system restarts or updates.

### Test Endpoint
When integrated into the ENCRYPT platform, this tool is triggered and receives input from the Data Preprocessing Tool 
through a sequence of API requests. Alternatively, the KGs' pipeline can be instantiated using a dedicated test endpoint.
- Step 1: Place the data files in the same directory as the downloader would after receiving them from the Preprocessing container. <br>
  Example: ```.KnowledgeGraphsData/KnowledgeGraphsJava/Use_Case/fintech/${DatasetName}/Downloaded_Data/person.csv``` <br>
  The ```${DatasetName}``` value corresponds to the DatasetName attribute in the ```.KnowledgeGraphsData/KnowledgeGraphsJava/ConfigFiles/fintech_csv_Config.json```
- Step 2: Use Postman to call the endpoint. <br>
  Example: ```POST http://localhost:7530/KGInputPoint/testPipeline?UseCase=fintech&filename=whatever.csv``` <br>
  The KG will be generated from all the csv file in the ```Downloaded_Data``` directory. 
  Therefore, only the file extension (```.csv``) will be utilized to determine the dataset.