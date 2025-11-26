package com.secufusion.iam.controller;

import com.secufusion.iam.dto.CountryDTO;
import com.secufusion.iam.entity.Country;
import com.secufusion.iam.service.CountryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/countries")
public class CountryController {

    @Autowired
    private CountryService countryService;

    @GetMapping("/all")
    public List<CountryDTO> getCountries() {
        return countryService.getAllCountries();
    }

    @GetMapping
    public ResponseEntity<List<Country>> getCountriesByRegion(@RequestParam Long regionId){
        return ResponseEntity.ok(countryService.getCountriesByRegion(regionId));
    }
}
