package com.los.core.repository;

import com.los.core.model.entity.ApplicationParty;
import com.los.core.model.enums.ApplicationPartyRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationPartyRepository extends JpaRepository<ApplicationParty, UUID> {

    List<ApplicationParty> findByApplicationIdOrderBySequenceNoAsc(UUID applicationId);

    Optional<ApplicationParty> findByIdAndApplicationId(UUID id, UUID applicationId);

    Optional<ApplicationParty> findByApplicationIdAndRole(UUID applicationId, ApplicationPartyRole role);

    Optional<ApplicationParty> findByApplicationIdAndUserId(UUID applicationId, UUID userId);

    List<ApplicationParty> findByUserId(UUID userId);

    long countByApplicationIdAndRole(UUID applicationId, ApplicationPartyRole role);

    void deleteByApplicationIdAndRole(UUID applicationId, ApplicationPartyRole role);

    void deleteByApplicationIdAndIdIn(UUID applicationId, Collection<UUID> ids);

    @Query(value = """
            SELECT * FROM application_parties p
            WHERE lower(coalesce(p.personal_info->>'email','')) = lower(:email)
               OR lower(coalesce(p.personal_info->>'borrowerEmail','')) = lower(:email)
               OR lower(coalesce(p.personal_info->>'contactEmail','')) = lower(:email)
            """, nativeQuery = true)
    List<ApplicationParty> findByContactEmail(@Param("email") String email);

    @Query(value = """
            SELECT * FROM application_parties p
            WHERE regexp_replace(
                    coalesce(p.personal_info->>'mobile', coalesce(p.personal_info->>'phone','')),
                    '[^0-9]', '', 'g'
                  ) = :mobileDigits
            """, nativeQuery = true)
    List<ApplicationParty> findByMobileDigits(@Param("mobileDigits") String mobileDigits);

    @Query(value = """
            SELECT * FROM application_parties p
            WHERE p.application_id <> :excludeAppId
              AND (
                lower(coalesce(p.personal_info->>'email','')) = lower(:email)
                OR lower(coalesce(p.personal_info->>'borrowerEmail','')) = lower(:email)
                OR lower(coalesce(p.personal_info->>'contactEmail','')) = lower(:email)
              )
            """, nativeQuery = true)
    List<ApplicationParty> findOthersByEmail(
            @Param("excludeAppId") UUID excludeAppId,
            @Param("email") String email);

    @Query(value = """
            SELECT * FROM application_parties p
            WHERE p.application_id <> :excludeAppId
              AND regexp_replace(
                    coalesce(p.personal_info->>'mobile', coalesce(p.personal_info->>'phone','')),
                    '[^0-9]', '', 'g'
                  ) = :mobileDigits
            """, nativeQuery = true)
    List<ApplicationParty> findOthersByMobileDigits(
            @Param("excludeAppId") UUID excludeAppId,
            @Param("mobileDigits") String mobileDigits);

    @Query(value = """
            SELECT * FROM application_parties p
            WHERE p.application_id <> :excludeAppId
              AND upper(coalesce(p.personal_info->>'panNumber','')) = upper(:pan)
            """, nativeQuery = true)
    List<ApplicationParty> findOthersByPan(
            @Param("excludeAppId") UUID excludeAppId,
            @Param("pan") String pan);
}
