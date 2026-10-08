package com.example.employeedirectory;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class EmployeeForm {

    @NotBlank(message = "Employee ID is required.")
    @Pattern(regexp = "EMP-[0-9]{4,6}", message = "Use the format EMP-1001 (4 to 6 digits).")
    private String employeeId;

    @NotBlank(message = "Name is required.")
    @Size(max = 80, message = "Name must be 80 characters or fewer.")
    private String name;

    @NotBlank(message = "Choose a department.")
    @Pattern(regexp = "Engineering|Human Resources|Finance|Marketing|Operations",
            message = "Choose a department from the list.")
    private String department;

    @NotBlank(message = "Designation is required.")
    @Size(max = 80, message = "Designation must be 80 characters or fewer.")
    private String designation;

    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 254, message = "Email must be 254 characters or fewer.")
    private String email;

    public EmployeeForm() {
    }

    public EmployeeForm(Employee employee) {
        employeeId = employee.employeeId();
        name = employee.name();
        department = employee.department();
        designation = employee.designation();
        email = employee.email();
    }

    public Employee toEmployee() {
        return new Employee(employeeId.trim(), name.trim(), department.trim(), designation.trim(), email.trim());
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
