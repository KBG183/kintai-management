package com.example.kintai.domain.store.service;

import com.example.kintai.domain.store.mapper.StoreAuthMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreAuthorizationServicePayrollOwnershipTest {

    @Mock
    private StoreAuthMapper storeAuthMapper;

    @InjectMocks
    private StoreAuthorizationService storeAuthorizationService;

    @Test
    void payrollOwnedByAnotherStoreMustBeDenied() {
        when(storeAuthMapper.findOwnerIdByPayrollId(900L)).thenReturn(42L);

        assertThrows(AccessDeniedException.class,
                () -> storeAuthorizationService.validatePayrollOwnership(900L, 17L));

        verify(storeAuthMapper).findOwnerIdByPayrollId(900L);
    }

    @Test
    void payrollOwnedByCurrentMemberMustBeAllowed() {
        when(storeAuthMapper.findOwnerIdByPayrollId(901L)).thenReturn(17L);

        assertDoesNotThrow(() -> storeAuthorizationService.validatePayrollOwnership(901L, 17L));

        verify(storeAuthMapper).findOwnerIdByPayrollId(901L);
    }
}
