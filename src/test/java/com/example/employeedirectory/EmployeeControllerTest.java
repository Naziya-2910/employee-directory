package com.example.employeedirectory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(EmployeeController.class)
@Import({EmployeeService.class, PageMetadataAdvice.class})
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeService employeeService;

    @Test
    void filtersTheServerRenderedEmployeeDirectory() throws Exception {
        mockMvc.perform(get("/employees").param("q", "amara").param("department", "Engineering"))
                .andExpect(status().isOk())
                .andExpect(view().name("employees"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("Amara Patel", "1 employee(s) shown"));

        mockMvc.perform(get("/employees").param("q", "amara").param("department", "Finance"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("No employees found"));
    }

    @Test
    void displaysValidationErrorsAndDoesNotCreateInvalidEmployees() throws Exception {
        mockMvc.perform(post("/employees")
                        .param("employeeId", "invalid")
                        .param("name", "")
                        .param("department", "Unknown")
                        .param("designation", "")
                        .param("email", "not-an-email"))
                .andExpect(status().isOk())
                .andExpect(view().name("employee-form"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("Use the format EMP-1001", "Enter a valid email address."));

        assertThat(employeeService.findById("invalid")).isEmpty();
    }

    @Test
    void createsEmployeesAndExposesOnlyTheInfrastructureHealthCheck() throws Exception {
        mockMvc.perform(post("/employees")
                        .param("employeeId", "EMP-2001")
                        .param("name", "Morgan Ellis")
                        .param("department", "Operations")
                        .param("designation", "Office Manager")
                        .param("email", "morgan.ellis@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employees"));

        assertThat(employeeService.findById("EMP-2001")).isPresent();

        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("\"status\":\"UP\""));
    }
}
