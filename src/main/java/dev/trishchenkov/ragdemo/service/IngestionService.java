package dev.trishchenkov.ragdemo.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.UnknownHostException;
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
        requirePublicHttpUrl(url);
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

    /** Blocks the SSRF class of attacks: an ingestion request must not be able to reach internal hosts. */
    static void requirePublicHttpUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL: " + url, e);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only http/https URLs are allowed: " + url);
        }
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("URL has no host: " + url);
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("Refusing to fetch an internal/private address: " + host);
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Cannot resolve host: " + host, e);
        }
    }

    private int store(List<Document> documents) {
        List<Document> chunks = splitter.apply(documents);
        vectorStore.add(chunks);
        return chunks.size();
    }
}
