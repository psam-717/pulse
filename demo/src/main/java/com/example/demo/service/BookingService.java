package com.example.demo.service;

import com.example.demo.dto.BookingRequest;
import com.example.demo.dto.BookingResponse;
import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class BookingService {

    private final HospitalRepository hospitalRepository;
    private final DepartmentRepository departmentRepository;
    private final DoctorRepository doctorRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final BookingRepository bookingRepository;
    private final PatientRepository patientRepository;

    public BookingService(HospitalRepository hospitalRepository,
                          DepartmentRepository departmentRepository,
                          DoctorRepository doctorRepository,
                          TimeSlotRepository timeSlotRepository,
                          BookingRepository bookingRepository,
                          PatientRepository patientRepository) {
        this.hospitalRepository = hospitalRepository;
        this.departmentRepository = departmentRepository;
        this.doctorRepository = doctorRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.bookingRepository = bookingRepository;
        this.patientRepository = patientRepository;
    }

    public List<Hospital> listHospitals() {
        return hospitalRepository.findAll();
    }

    public List<Department> listDepartments(Long hospitalId) {
        if (!hospitalRepository.existsById(hospitalId)) {
            throw new IllegalArgumentException("Hospital not found");
        }
        return departmentRepository.findByHospitalId(hospitalId);
    }

    public List<Doctor> listDoctors(Long departmentId) {
        if (!departmentRepository.existsById(departmentId)) {
            throw new IllegalArgumentException("Department not found");
        }
        return doctorRepository.findByDepartmentId(departmentId);
    }

    public List<TimeSlot> listAvailableSlots(Long doctorId, LocalDate date) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new IllegalArgumentException("Doctor not found");
        }
        return timeSlotRepository.findByDoctorIdAndDateAndIsBooked(doctorId, date, false);
    }

    @Transactional
    public BookingResponse createBooking(BookingRequest request) {
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));

        TimeSlot slot = timeSlotRepository.findById(request.timeSlotId())
                .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        if (slot.isBooked()) {
            throw new IllegalStateException("This time slot is already booked");
        }

        Doctor doctor = slot.getDoctor();
        Department department = doctor.getDepartment();
        Hospital hospital = doctor.getHospital();

        slot.setBooked(true);
        timeSlotRepository.save(slot);

        Booking booking = new Booking(patient, doctor, department, hospital, slot, department.getConsultationFee());
        bookingRepository.save(booking);

        return toResponse(booking);
    }

    @Transactional
    public BookingResponse updatePaymentStatus(Long bookingId, String paymentStatusStr) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        PaymentStatus paymentStatus;
        try {
            paymentStatus = PaymentStatus.valueOf(paymentStatusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid payment status: " + paymentStatusStr);
        }

        booking.setPaymentStatus(paymentStatus);
        if (paymentStatus == PaymentStatus.PAID) {
            booking.setStatus(BookingStatus.CONFIRMED);
        } else if (paymentStatus == PaymentStatus.FAILED) {
            booking.setStatus(BookingStatus.CANCELLED);
            // Free the slot back up
            booking.getTimeSlot().setBooked(false);
            timeSlotRepository.save(booking.getTimeSlot());
        }

        bookingRepository.save(booking);
        return toResponse(booking);
    }

    public BookingResponse getBookingSummary(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        return toResponse(booking);
    }

    private BookingResponse toResponse(Booking booking) {
        String patientName = booking.getPatient().getFirstName() + " " + booking.getPatient().getLastName();
        String doctorName = "Dr. " + booking.getDoctor().getFirstName() + " " + booking.getDoctor().getLastName();
        return new BookingResponse(
                booking.getId(),
                patientName,
                doctorName,
                booking.getDepartment().getName(),
                booking.getHospital().getName(),
                booking.getTimeSlot().getDate(),
                booking.getTimeSlot().getStartTime(),
                booking.getTimeSlot().getEndTime(),
                booking.getBookingDate(),
                booking.getStatus().name(),
                booking.getPaymentStatus().name(),
                booking.getAmountDue()
        );
    }
}
