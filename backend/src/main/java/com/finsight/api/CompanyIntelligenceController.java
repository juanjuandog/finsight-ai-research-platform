package com.finsight.api;

import com.finsight.application.CompanyIntelligenceService;
import com.finsight.domain.model.CompanyEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/intelligence")
public class CompanyIntelligenceController {
    private final CompanyIntelligenceService companyIntelligenceService;

    public CompanyIntelligenceController(CompanyIntelligenceService companyIntelligenceService) {
        this.companyIntelligenceService = companyIntelligenceService;
    }

    @PostMapping("/{companySymbol}/rebuild")
    public CompanyIntelligenceService.BuildResult rebuild(@PathVariable String companySymbol) {
        return companyIntelligenceService.rebuild(companySymbol);
    }

    @GetMapping("/{companySymbol}/timeline")
    public List<CompanyEvent> timeline(@PathVariable String companySymbol) {
        return companyIntelligenceService.timeline(companySymbol);
    }

    @GetMapping("/{companySymbol}/graph")
    public CompanyIntelligenceService.CompanyGraph graph(@PathVariable String companySymbol) {
        return companyIntelligenceService.graph(companySymbol);
    }
}

