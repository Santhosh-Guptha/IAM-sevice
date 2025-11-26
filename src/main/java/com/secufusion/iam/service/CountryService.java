package com.secufusion.iam.service;


import com.secufusion.iam.dto.CountryDTO;
import com.secufusion.iam.entity.Country;
import com.secufusion.iam.repository.CountryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CountryService {

    @Autowired
    private CountryRepository countryRepository;

    public List<CountryDTO> getAllCountries() {
        return countryRepository.findAll()
                .stream()
                .map(country ->
                        new CountryDTO(country.getPkCountryId(), country.getCountryName())
                )
                .toList();
    }

    public List<Country> getCountriesByRegion(Long regionId){
        return countryRepository.findAllByRegionId(regionId);
    }
}
