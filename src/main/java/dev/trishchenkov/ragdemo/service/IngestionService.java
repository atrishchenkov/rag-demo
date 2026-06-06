package dev.trishchenkov.ragdemo.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;

@Service
public class IngestionService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter = new TokenTextSplitter();

    IngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public int ingestText(String text, Map<String, Object> metadata) {
        return store(List.of(new Document(text, metadata != null ? metadata : Map.of())));
    }

    public int ingestFromUrl(String url) {
        try {
            return store(new TikaDocumentReader(new UrlResource(url)).read());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL: " + url, e);
        }
    }

    public int ingestFile(Resource resource) {
        return store(new TikaDocumentReader(resource).read());
    }

    public void deleteByFilter(String filterExpression) {
        vectorStore.delete(filterExpression);
    }

    private int store(List<Document> documents) {
        List<Document> chunks = splitter.apply(documents);
        vectorStore.add(chunks);
        return chunks.size();
    }
}
