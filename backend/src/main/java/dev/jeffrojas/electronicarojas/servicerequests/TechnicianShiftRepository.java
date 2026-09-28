package dev.jeffrojas.electronicarojas.servicerequests;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TechnicianShiftRepository extends JpaRepository<TechnicianShift, Long> {

	@Query("select s from TechnicianShift s join fetch s.branch where s.technician.id = :technicianId order by s.dayOfWeek")
	List<TechnicianShift> findForTechnician(long technicianId);

	@Query("select s from TechnicianShift s where s.technician.id = :technicianId and s.dayOfWeek = :dayOfWeek")
	Optional<TechnicianShift> findDay(long technicianId, short dayOfWeek);

}
