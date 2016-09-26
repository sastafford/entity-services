package com.marklogic.entityservices.msb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.document.DocumentWriteOperation;
import com.marklogic.client.document.ServerTransform;
import com.marklogic.client.helper.DatabaseClientProvider;
import com.marklogic.client.io.DocumentMetadataHandle;
import com.marklogic.client.io.Format;
import com.marklogic.client.io.JacksonHandle;
import com.marklogic.client.io.MarkLogicWriteHandle;
import com.marklogic.client.query.StructuredQueryBuilder;
import com.marklogic.client.query.StructuredQueryDefinition;
import com.marklogic.datamovement.ApplyTransformListener;
import com.marklogic.datamovement.DataMovementManager;
import com.marklogic.datamovement.JobTicket;
import com.marklogic.datamovement.QueryHostBatcher;
import com.marklogic.spring.batch.item.MarkLogicItemWriter;
import com.marklogic.spring.batch.item.processor.ResourceToDocumentWriteOperationItemProcessor;
import com.marklogic.spring.batch.item.reader.EnhancedResourcesItemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.batch.item.*;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.validation.BindException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@EnableBatchProcessing
public class EntityServicesExampleConfig implements EnvironmentAware {
    
    private Environment env;
    
    //Rename this private variable
    private final String JOB_NAME = "yourJob";
    
    protected String projectDir;
    
    private static Logger logger = LoggerFactory.getLogger(EntityServicesExampleConfig.class);
    
    @Bean
    public Job job(
        JobBuilderFactory jobBuilderFactory,
        @Qualifier("importJsonData") Step importJsonData,
        @Qualifier("importCsvData") Step importCsvData,
        @Qualifier("harmonizerStep") Step harmonizer
    ) {
        return jobBuilderFactory.get(JOB_NAME)
                .start(importJsonData)
                .next(importCsvData)
                .next(harmonizer)
                .build();
    }
    
    @Bean
    @JobScope
    public Step importJsonData(
            StepBuilderFactory stepBuilderFactory,
            DatabaseClientProvider databaseClientProvider,
            @Value("#{jobParameters['output_collections']}") String[] collections) {
        
        DatabaseClient databaseClient = databaseClientProvider.getDatabaseClient();
        Path currentRelativePath = Paths.get("");
        projectDir = currentRelativePath.toAbsolutePath().toString();
        logger.debug("Current relative path is: " + projectDir);
        String inputFilePath = projectDir + "/data/race-data";
        String inputFilePattern = ".*.json";
        DocumentMetadataHandle metadata = new DocumentMetadataHandle().withCollections("raw");
        ResourceToDocumentWriteOperationItemProcessor processor = new ResourceToDocumentWriteOperationItemProcessor();
        processor.setFormat(Format.JSON);
        processor.setMetadataHandle(metadata);
        ItemWriter<DocumentWriteOperation> writer = new MarkLogicItemWriter(databaseClient);
        
        return stepBuilderFactory.get("importJsonData")
                .<Resource, DocumentWriteOperation>chunk(10)
                .reader(new EnhancedResourcesItemReader(inputFilePath, inputFilePattern))
                .processor(processor)
                .writer(writer)
                .build();
    }
    
    @Bean
    @JobScope
    public Step importCsvData(
            StepBuilderFactory stepBuilderFactory,
            DatabaseClientProvider databaseClientProvider) {
        
        FlatFileItemReader<Run> itemReader = new FlatFileItemReader<Run>();
        itemReader.setResource(new FileSystemResource("data/third-party/csv/2016-angel-island.csv"));
        itemReader.setLinesToSkip(1);
        DefaultLineMapper<Run> lineMapper = new DefaultLineMapper<Run>();
        lineMapper.setLineTokenizer(new DelimitedLineTokenizer());
        lineMapper.setFieldSetMapper(new RunFieldSetMapper());
        itemReader.setLineMapper(lineMapper);
        itemReader.open(new ExecutionContext());
        
        ItemProcessor<Run, DocumentWriteOperation> itemProcessor = new ItemProcessor<Run, DocumentWriteOperation>() {
            @Override
            public DocumentWriteOperation process(Run item) throws Exception {
                String uri = "2016-angel-island.csv-" + item.getBib() + ".json";
                DocumentMetadataHandle metadata = new DocumentMetadataHandle().withCollections("raw", "csv");
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.convertValue(item, JsonNode.class);
                MarkLogicWriteHandle handle = new MarkLogicWriteHandle(uri, metadata, new JacksonHandle(node));
                return handle;
            }
        };
        
        ItemWriter<DocumentWriteOperation> itemWriter = new MarkLogicItemWriter(databaseClientProvider.getDatabaseClient());
        
        return stepBuilderFactory.get("importCsvData")
                .<Run, DocumentWriteOperation>chunk(10)
                .reader(itemReader)
                .processor(itemProcessor)
                .writer(itemWriter)
                .build();
    }
    
    @Bean
    @JobScope
    public Step harmonizerStep(
            StepBuilderFactory stepBuilderFactory,
            DatabaseClientProvider databaseClientProvider) {
        
        DataMovementManager moveMgr = DataMovementManager.newInstance().withClient(databaseClientProvider.getDatabaseClient());
        StructuredQueryBuilder qb = new StructuredQueryBuilder();
        StructuredQueryDefinition qdef = qb.collection("raw");
        ServerTransform ingester = new ServerTransform("ingester");
        ApplyTransformListener listener = new ApplyTransformListener().withTransform(ingester)
                .withApplyResult(ApplyTransformListener.ApplyResult.IGNORE).onSuccess((dbClient, inPlaceBatch) -> {
                    logger.debug("Batch transform SUCCESS");
                }).onBatchFailure((dbClient, inPlaceBatch, throwable) -> {
                    System.err.println(throwable.getMessage());
                    System.err.print(String.join("\n", inPlaceBatch.getItems()) + "\n");
                });
        
        Tasklet tasklet = new Tasklet() {
            @Override
            public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
                QueryHostBatcher queryHostBatcher = moveMgr.newQueryHostBatcher(qdef).withBatchSize(100)
                        .withThreadCount(5).onUrisReady(listener).onQueryFailure((client3, exception) -> {
                            logger.error("Query error");
                        });
    
                JobTicket ticket = moveMgr.startJob(queryHostBatcher);
                queryHostBatcher.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
                moveMgr.stopJob(ticket);
                return RepeatStatus.FINISHED;
            }
        };
        return stepBuilderFactory.get("harmonizer").tasklet(tasklet).build();
    }
    
    
    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
    
    public static class RunFieldSetMapper implements FieldSetMapper<Run> {
    
        @Override
        public Run mapFieldSet(FieldSet fieldSet) throws BindException {
            Run run = new Run();
            run.setPlace(fieldSet.readString(0));
            run.setBib(fieldSet.readInt(1));
            run.setName(fieldSet.readString(2));
            return run;
        }
    }
}
