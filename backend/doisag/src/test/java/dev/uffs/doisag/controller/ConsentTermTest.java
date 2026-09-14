package dev.uffs.doisag.controller;

import dev.uffs.doisag.service.ConsentTermService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// termo de consentimento do cadastro (RF36)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConsentTermTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anyoneCanReadTheCurrentTermBeforeHavingAnAccount() throws Exception {
        mockMvc.perform(get("/consent-term"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(ConsentTermService.CURRENT_VERSION))
                .andExpect(jsonPath("$.text").value(containsString("13.709/2018")));
    }
}
