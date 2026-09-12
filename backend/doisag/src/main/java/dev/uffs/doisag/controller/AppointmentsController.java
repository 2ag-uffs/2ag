package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.service.AppointmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/consulta")

public class AppointmentsController {

    private final AppointmentService appointmentService;

    public AppointmentsController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    // create
    @PreAuthorize("hasRole('PRESCRIBER')")
    @PostMapping
    public Appointment create(@RequestBody Appointment appointment) {
        return appointmentService.create(appointment);
    }

    // read all
    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping
    public List<Appointment> getAll() {
        return appointmentService.getAll();
    }

    // read by id
    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping("/{id}")
    public ResponseEntity<Appointment> getById(@PathVariable Long id) {
        Appointment appointment = appointmentService.getById(id);
        return ResponseEntity.ok(appointment);
    }

    // update
    @PreAuthorize("hasRole('PRESCRIBER')")
    @PutMapping("/{id}")
    public ResponseEntity<Appointment> update(@PathVariable Long id, @RequestBody Appointment appointmentDetails) {
        try {
            Appointment updatedAppointment = appointmentService.update(id, appointmentDetails);
            return ResponseEntity.ok(updatedAppointment);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    // delete appointment
    @PreAuthorize("hasRole('PRESCRIBER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            appointmentService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
