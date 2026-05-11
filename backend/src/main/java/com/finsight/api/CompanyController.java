package com.finsight.api;

import com.finsight.domain.model.Company;
import com.finsight.domain.model.FinancialDocument;
import com.finsight.domain.repository.CompanyRepository;
import com.finsight.domain.repository.DocumentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {
    private final CompanyRepository companyRepository;
    private final DocumentRepository documentRepository;

    public CompanyController(CompanyRepository companyRepository, DocumentRepository documentRepository) {
        this.companyRepository = companyRepository;
        this.documentRepository = documentRepository;
    }

    @GetMapping
    public List<Company> companies() {
        return companyRepository.findAll();
    }

    @GetMapping("/{companySymbol}/documents")
    public List<FinancialDocument> documents(@PathVariable String companySymbol) {
        return documentRepository.findByCompanySymbol(companySymbol);
    }
}

