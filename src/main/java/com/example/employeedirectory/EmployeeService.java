package com.example.employeedirectory;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmployeeService {

    private final Map<String, Employee> employees = new ConcurrentHashMap<>();

    public EmployeeService() {
        resetToSampleData();
    }

    public List<Employee> findAll(String query, String department) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String normalizedDepartment = department == null ? "" : department.trim();
        List<Employee> matchingEmployees = new ArrayList<>();
        for (Employee employee : employees.values()) {
            boolean matchesDepartment = normalizedDepartment.isEmpty()
                    || employee.department().equals(normalizedDepartment);
            boolean matchesQuery = normalizedQuery.isEmpty()
                    || employee.employeeId().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || employee.name().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || employee.department().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || employee.designation().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || employee.email().toLowerCase(Locale.ROOT).contains(normalizedQuery);
            if (matchesDepartment && matchesQuery) {
                matchingEmployees.add(employee);
            }
        }
        matchingEmployees.sort((left, right) ->
                String.CASE_INSENSITIVE_ORDER.compare(left.name(), right.name()));
        return List.copyOf(matchingEmployees);
    }

    public Map<String, Long> countByDepartment() {
        Map<String, Long> counts = new TreeMap<>();
        for (Employee employee : employees.values()) {
            counts.merge(employee.department(), 1L, (current, increment) -> current + increment);
        }
        return counts;
    }

    public Optional<Employee> findById(String employeeId) {
        return Optional.ofNullable(employees.get(employeeId));
    }

    public boolean existsById(String employeeId) {
        return employees.containsKey(employeeId);
    }

    public synchronized void create(Employee employee) {
        if (employees.putIfAbsent(employee.employeeId(), employee) != null) {
            throw new IllegalArgumentException("Employee ID already exists.");
        }
    }

    public synchronized boolean update(String employeeId, Employee employee) {
        if (!employees.containsKey(employeeId)) {
            return false;
        }
        employees.put(employeeId, employee);
        return true;
    }

    public synchronized boolean delete(String employeeId) {
        return employees.remove(employeeId) != null;
    }

    public synchronized void resetToSampleData() {
        employees.clear();
        employees.put("EMP-1001", new Employee("EMP-1001", "Amara Patel", "Engineering",
                "Software Engineer", "amara.patel@example.com"));
        employees.put("EMP-1002", new Employee("EMP-1002", "Jordan Lee", "Human Resources",
                "People Partner", "jordan.lee@example.com"));
        employees.put("EMP-1003", new Employee("EMP-1003", "Sam Rivera", "Finance",
                "Financial Analyst", "sam.rivera@example.com"));
        employees.put("EMP-1004", new Employee("EMP-1004", "Taylor Morgan", "Engineering",
                "Platform Engineer", "taylor.morgan@example.com"));
        employees.put("EMP-1005", new Employee("EMP-1005", "Casey Okafor", "Marketing",
                "Content Strategist", "casey.okafor@example.com"));
    }
}
