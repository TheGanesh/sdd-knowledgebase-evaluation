package com.ganesh.commerce.inventory.repository;

import com.ganesh.commerce.inventory.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, String> {
}
