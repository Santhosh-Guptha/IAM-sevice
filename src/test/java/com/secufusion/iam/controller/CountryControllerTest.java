package com.secufusion.iam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secufusion.iam.dto.CountryDTO;
import com.secufusion.iam.entity.Country;
import com.secufusion.iam.service.CountryService;
import com.secufusion.iam.service.InitializerExecutor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CountryController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CountryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CountryService countryService;

    @MockitoBean
    private InitializerExecutor initializerExecutor; // Prevents app startup error

    @Autowired
    private ObjectMapper objectMapper;

    // ---------------------------------------------------------
    // GET /countries/all
    // ---------------------------------------------------------
    @Test
    void testGetAllCountries() throws Exception {

        CountryDTO c1 = new CountryDTO(1L, "India");
        CountryDTO c2 = new CountryDTO(2L, "USA");

        when(countryService.getAllCountries())
                .thenReturn(List.of(c1, c2));

        mockMvc.perform(get("/countries/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].countryname").value("India"))
                .andExpect(jsonPath("$[1].countryname").value("USA"));
    }

    // ---------------------------------------------------------
    // GET /countries?regionId=100
    // ---------------------------------------------------------
    @Test
    void testGetCountriesByRegion() throws Exception {

        Country c1 = new Country();
        c1.setPkCountryId(1L);
        c1.setCountryName("Japan");

        Country c2 = new Country();
        c2.setPkCountryId(2L);
        c2.setCountryName("Korea");

        when(countryService.getCountriesByRegion(100L))
                .thenReturn(List.of(c1, c2));

        mockMvc.perform(get("/countries")
                        .param("regionId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].countryName").value("Japan"))
                .andExpect(jsonPath("$[1].countryName").value("Korea"));
    }
}
