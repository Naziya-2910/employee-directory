package com.example.employeedirectory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmployeeServiceTest {

    private EmployeeService employeeService;

    @BeforeEach
    void setUp() {
        employeeService = new EmployeeService();
    }

    @Test
    void searchesCaseInsensitivelyAcrossEmployeeFieldsAndDepartment() {
        assertThat(employeeService.findAll("AMARA", ""))
                .containsExactly(new Employee("EMP-1001", "Amara Patel", "Engineering",
                        "Software Engineer", "amara.patel@example.com"));
        assertThat(employeeService.findAll("", "Engineering"))
                .containsExactly(
                        new Employee("EMP-1001", "Amara Patel", "Engineering",
                                "Software Engineer", "amara.patel@example.com"),
                        new Employee("EMP-1004", "Taylor Morgan", "Engineering",
                                "Platform Engineer", "taylor.morgan@example.com"));
        assertThat(employeeService.findAll("finance", "Engineering")).isEmpty();
    }

    @Test
    void createsUpdatesAndDeletesEmployees() {
        Employee employee = new Employee("EMP-1006", "Alex Morgan", "Operations",
                "Product Designer", "alex.morgan@example.com");

        employeeService.create(employee);
        assertThat(employeeService.findById("EMP-1006")).contains(employee);

        Employee updated = new Employee("EMP-1006", "Alex Morgan", "Marketing",
                "Senior Product Designer", "alex.morgan@example.com");
        assertThat(employeeService.update("EMP-1006", updated)).isTrue();
        assertThat(employeeService.findById("EMP-1006")).contains(updated);

        assertThat(employeeService.delete("EMP-1006")).isTrue();
        assertThat(employeeService.findById("EMP-1006")).isEmpty();
        assertThat(employeeService.delete("EMP-1006")).isFalse();
    }

    @Test
    void rejectsDuplicateEmployeeIds() {
        Employee duplicate = new Employee("EMP-1001", "Alex Morgan", "Operations",
                "Product Designer", "alex.morgan@example.com");

        assertThatThrownBy(() -> employeeService.create(duplicate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Employee ID already exists.");
    }

    @Test
    void countsEmployeesByDepartment() {
        assertThat(employeeService.countByDepartment())
                .containsEntry("Engineering", 2L)
                .containsEntry("Finance", 1L)
                .containsEntry("Human Resources", 1L)
                .containsEntry("Marketing", 1L);
    }
}
