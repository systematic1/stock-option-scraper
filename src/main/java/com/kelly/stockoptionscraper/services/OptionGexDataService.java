package com.kelly.stockoptionscraper.services;

import com.kelly.stockoptionscraper.models.OptionGexData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptionGexDataService extends JpaRepository<OptionGexData, Integer> {
}
