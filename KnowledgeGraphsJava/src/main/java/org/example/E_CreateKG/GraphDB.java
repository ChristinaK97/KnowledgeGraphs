package org.example.E_CreateKG;

import com.ontotext.graphdb.repository.http.GraphDBHTTPRepository;
import com.ontotext.graphdb.repository.http.GraphDBHTTPRepositoryBuilder;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.impl.TreeModel;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.config.RepositoryConfig;
import org.eclipse.rdf4j.repository.config.RepositoryConfigSchema;
import org.eclipse.rdf4j.repository.manager.RepositoryManager;
import org.eclipse.rdf4j.repository.manager.RepositoryProvider;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.example.A_Coordinator.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

import static org.example.A_Coordinator.config.Config.GraphDBEndpoint;
import static org.example.A_Coordinator.config.Config.resourcesPath;
import static org.example.util.FileHandler.*;

public class GraphDB {
    private String repoId;
    private RepositoryManager repoManager;
    private RepositoryConnection connection;
    private static final Logger logger = LoggerFactory.getLogger(GraphDB.class);

    public GraphDB(boolean rewrite) {
        try {
            // create repository
            this.repoId = Pipeline.config.In.DatasetName;
            repoManager = RepositoryProvider.getRepositoryManager(GraphDBEndpoint + "/");
            repoManager.init();

            if(!repoExists() || rewrite)
                createRepository();
            printRepos();

            connection = startConnection();

            uploadData();
            if(connection. isOpen()){
                connection.close();
                repoManager.shutDown();
            }

        }catch (Exception e){
            e.printStackTrace();
        }
    }
// ----------------------------------------------------------------------------------------------------

    // TODO: replace deprecated
    // NAMESPACE = http://www.openrdf.org/config/repository#
    // REPOSITORY = http://www.openrdf.org/config/repository#Repository
    // REPOSITORYID = http://www.openrdf.org/config/repository#repositoryID


    private void createRepository() throws Exception{
        // https://graphdb.ontotext.com/documentation/10.4/using-graphdb-with-the-rdf4j-api.html
        logger.info("> CREATE REPOSITORY");

        if (repoExists()) // remove and create again
            repoManager.removeRepository(this.repoId);

        TreeModel graph = new TreeModel();

        InputStream config = new FileInputStream(repoConfigFile());
        RDFParser rdfParser = Rio.createParser(RDFFormat.TURTLE);
        rdfParser.setRDFHandler(new StatementCollector(graph));

        rdfParser.parse(config, RepositoryConfigSchema.NAMESPACE);
        config.close();

        Resource repositoryNode = Models.subject(graph.filter(null, RDF.TYPE,
                RepositoryConfigSchema.REPOSITORY)).orElse(null);

        graph.add(repositoryNode, RepositoryConfigSchema.REPOSITORYID,
                SimpleValueFactory.getInstance().createLiteral(this.repoId));

        RepositoryConfig repositoryConfig = RepositoryConfig.create(graph, repositoryNode);
        repoManager.addRepositoryConfig(repositoryConfig);
    }


    private RepositoryConnection startConnection() {
        GraphDBHTTPRepository repository = new GraphDBHTTPRepositoryBuilder()
                .withServerUrl(GraphDBEndpoint)
                .withRepositoryId(this.repoId)
                .build();
        return repository.getConnection();
    }

// ----------------------------------------------------------------------------------------------------
    public String repoConfigFile() {
        String configTemplateFilePath = getPath(String.format(
                "%s/ConfigFiles/graphdb_config_template_file.ttl", resourcesPath));
        String configRepoFilePath = getPath(String.format(
                "%s/ConfigFiles/graphdb_config_repo_file_modified.ttl", resourcesPath));

        try {
            File configTemplateFile = new File(configTemplateFilePath);
            BufferedReader reader = new BufferedReader(new FileReader(configTemplateFile));

            StringBuilder stringBuilder = new StringBuilder();
            String line, modifiedLine;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (count < 2) {
                    modifiedLine = line.replace("repoId", this.repoId);
                    if (!line.equals(modifiedLine))
                        ++count;
                }else
                    modifiedLine = line;

                stringBuilder.append(modifiedLine).append("\n");
            }
            reader.close();

            FileWriter writer = new FileWriter(configRepoFilePath);
            writer.write(stringBuilder.toString());
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return configRepoFilePath;
    }

// ----------------------------------------------------------------------------------------------------
    public void uploadData() throws IOException {

        logger.info("> UPLOAD DATA...");
        // When adding data we need to start a transaction
        connection.begin();

        String DOExtension = getFileExtension(Pipeline.config.DOMap.TgtOntology);
        RDFFormat DOFormat = "ttl".equals(DOExtension) ? RDFFormat.TURTLE : RDFFormat.RDFXML;

        connection.add(new FileInputStream(Pipeline.config.DOMap.TgtOntology), "urn:base", DOFormat);
        connection.add(new FileInputStream(Pipeline.config.Out.FullGraph), "urn:base", RDFFormat.TURTLE);
        // Committing the transaction persists the data
        connection.commit();
        logger.info("Full graph uploaded to repo " + this.repoId);
    }


// ----------------------------------------------------------------------------------------------------

    private void printRepos() {
        System.out.println(repoManager.getRepositoryIDs());
    }
    private boolean repoExists() {
        return repoManager.hasRepositoryConfig(this.repoId);
    }


}
