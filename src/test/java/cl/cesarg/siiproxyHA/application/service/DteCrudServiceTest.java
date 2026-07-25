package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.model.DteItem;
import cl.cesarg.siiproxyHA.domain.model.DteReference;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteItemRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteReferenceRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DteCrudServiceTest {

    @Test
    void createsDteGraphWithSingleCascadeSave() {
        DteRepository dteRepository = mock(DteRepository.class);
        DteItemRepository itemRepository = mock(DteItemRepository.class);
        DteReferenceRepository referenceRepository = mock(DteReferenceRepository.class);
        DteCrudService service = new DteCrudService(
                dteRepository, itemRepository, referenceRepository);

        Dte dte = new Dte();
        DteItem firstItem = new DteItem();
        DteItem secondItem = new DteItem();
        DteReference reference = new DteReference();
        dte.setItems(List.of(firstItem, secondItem));
        dte.setReferences(List.of(reference));
        when(dteRepository.saveAndFlush(dte)).thenReturn(dte);

        Dte result = service.create(dte);

        assertSame(dte, result);
        assertSame(dte, firstItem.getDte());
        assertSame(dte, secondItem.getDte());
        assertSame(dte, reference.getDte());
        verify(dteRepository).saveAndFlush(dte);
        verify(itemRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
        verify(referenceRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }
}
