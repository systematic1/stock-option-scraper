package com.kelly.stockoptionscraper.services;

import com.kelly.stockoptionscraper.models.YFOptionData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptionDataService extends JpaRepository<YFOptionData, String> {
}
