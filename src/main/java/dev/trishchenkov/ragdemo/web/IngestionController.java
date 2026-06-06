package dev.trishchenkov.ragdemo.web;

import dev.trishchenkov.ragdemo.dto.IngestRequest;
import dev.trishchenkov.ragdemo.dto.IngestResponse;
import dev.trishchenkov.ragdemo.dto.UrlRequest;
import dev.trishchenkov.ragdemo.service.IngestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
class IngestionController {

    private final IngestionService ingestionService;

    IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/documents")
    IngestResponse ingestText(@Valid @RequestBody IngestRequest request) {
        return new IngestResponse(ingestionService.ingestText(request.text(), request.metadata()));
    }

    @PostMapping("/documents/url")
    IngestResponse ingestUrl(@Valid @RequestBody UrlRequest request) {
        return new IngestResponse(ingestionService.ingestFromUrl(request.url()));
    }

    @PostMapping("/documents/file")
    IngestResponse ingestFile(@RequestParam("file") MultipartFile file) {
        return new IngestResponse(ingestionService.ingestFile(file.getResource()));
    }

    @DeleteMapping("/documents")
    void deleteByFilter(@RequestParam("filter") String filter) {
        ingestionService.deleteByFilter(filter);
    }
}
