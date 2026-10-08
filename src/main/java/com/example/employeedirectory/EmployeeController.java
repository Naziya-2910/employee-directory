package com.example.employeedirectory;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
public class EmployeeController {

    private static final List<String> DEPARTMENTS = List.of(
            "Engineering", "Human Resources", "Finance", "Marketing", "Operations");

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @ModelAttribute("departments")
    public List<String> departments() {
        return DEPARTMENTS;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("departmentCounts", employeeService.countByDepartment());
        model.addAttribute("employeeCount", employeeService.findAll("", "").size());
        model.addAttribute("employees", employeeService.findAll("", ""));
        return "dashboard";
    }

    @GetMapping("/employees")
    public String employees(@RequestParam(defaultValue = "") String q,
                            @RequestParam(defaultValue = "") String department,
                            Model model) {
        model.addAttribute("employees", employeeService.findAll(q, department));
        model.addAttribute("q", q);
        model.addAttribute("selectedDepartment", department);
        return "employees";
    }

    @GetMapping("/employees/new")
    public String newEmployee(Model model) {
        model.addAttribute("employeeForm", new EmployeeForm());
        model.addAttribute("pageTitle", "Add employee");
        model.addAttribute("formAction", "/employees");
        model.addAttribute("editing", false);
        return "employee-form";
    }

    @PostMapping("/employees")
    public String createEmployee(@Valid @ModelAttribute("employeeForm") EmployeeForm employeeForm,
                                 BindingResult bindingResult, Model model,
                                 RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasFieldErrors("employeeId")
                && employeeService.existsById(employeeForm.getEmployeeId())) {
            bindingResult.rejectValue("employeeId", "duplicate", "That employee ID is already in use.");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Add employee");
            model.addAttribute("formAction", "/employees");
            model.addAttribute("editing", false);
            return "employee-form";
        }
        try {
            employeeService.create(employeeForm.toEmployee());
        } catch (IllegalArgumentException exception) {
            bindingResult.rejectValue("employeeId", "duplicate", "Employee ID already exists.");
            model.addAttribute("pageTitle", "Add employee");
            model.addAttribute("formAction", "/employees");
            model.addAttribute("editing", false);
            return "employee-form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Employee added successfully.");
        return "redirect:/employees";
    }

    @GetMapping("/employees/{employeeId}/edit")
    public String editEmployee(@PathVariable String employeeId, Model model) {
        Employee employee = employeeService.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("employeeForm", new EmployeeForm(employee));
        model.addAttribute("pageTitle", "Edit employee");
        model.addAttribute("formAction", "/employees/" + employeeId);
        model.addAttribute("editing", true);
        return "employee-form";
    }

    @PostMapping("/employees/{employeeId}")
    public String updateEmployee(@PathVariable String employeeId,
                                 @Valid @ModelAttribute("employeeForm") EmployeeForm employeeForm,
                                 BindingResult bindingResult, Model model,
                                 RedirectAttributes redirectAttributes) {
        if (!employeeService.existsById(employeeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (!employeeId.equals(employeeForm.getEmployeeId())) {
            bindingResult.rejectValue("employeeId", "immutable", "Employee ID cannot be changed.");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Edit employee");
            model.addAttribute("formAction", "/employees/" + employeeId);
            model.addAttribute("editing", true);
            return "employee-form";
        }
        employeeService.update(employeeId, employeeForm.toEmployee());
        redirectAttributes.addFlashAttribute("successMessage", "Employee updated successfully.");
        return "redirect:/employees";
    }

    @PostMapping("/employees/{employeeId}/delete")
    public String deleteEmployee(@PathVariable String employeeId, RedirectAttributes redirectAttributes) {
        if (!employeeService.delete(employeeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        redirectAttributes.addFlashAttribute("successMessage", "Employee deleted successfully.");
        return "redirect:/employees";
    }

    @GetMapping("/healthz")
    public org.springframework.http.ResponseEntity<Map<String, String>> health() {
        return org.springframework.http.ResponseEntity.ok(Map.of("status", "UP"));
    }
}
