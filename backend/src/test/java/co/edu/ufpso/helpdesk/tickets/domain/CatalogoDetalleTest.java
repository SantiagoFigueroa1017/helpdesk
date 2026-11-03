package co.edu.ufpso.helpdesk.tickets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.tickets.domain.CatalogoDetalle.DefinicionModulo;

/**
 * El catálogo del formulario dinámico debe coincidir con los records del detalle (tarea 2.6). Si
 * alguien agrega un campo a un record y no al catálogo, o al revés, esta prueba falla.
 */
class CatalogoDetalleTest {

    @Test
    void tieneLosCincoModulosEnElOrdenDelEnum() {
        assertThat(CatalogoDetalle.todos()).extracting(DefinicionModulo::modulo)
                .containsExactly(ModuloErp.values());
        for (DefinicionModulo d : CatalogoDetalle.todos()) {
            assertThat(CatalogoDetalle.de(d.modulo())).isSameAs(d);
        }
    }

    @Test
    void losCamposSonLosComponentesDelRecordEnElMismoOrden() {
        for (DefinicionModulo d : CatalogoDetalle.todos()) {
            List<String> componentes = Arrays.stream(d.clase().getRecordComponents())
                    .map(RecordComponent::getName).toList();
            assertThat(d.campos()).extracting(CampoDetalle::nombre)
                    .as("campos de %s", d.modulo())
                    .containsExactlyElementsOf(componentes);
        }
    }

    @Test
    void cadaTipoDeCampoCorrespondeAlTipoDeJava() {
        for (DefinicionModulo d : CatalogoDetalle.todos()) {
            for (RecordComponent c : d.clase().getRecordComponents()) {
                CampoDetalle campo = d.campo(c.getName());
                Class<?> esperado = switch (campo.tipo()) {
                    case TEXTO, TEXTO_LARGO -> String.class;
                    case ENTERO -> Integer.class;
                    case DECIMAL -> Double.class;
                    case DINERO -> java.math.BigDecimal.class;
                    case OPCION -> c.getType();
                };
                assertThat(c.getType()).as("%s.%s", d.modulo(), c.getName()).isEqualTo(esperado);
                if (campo.tipo() == TipoCampo.OPCION) {
                    assertThat(c.getType().isEnum()).isTrue();
                    List<String> constantes = Arrays.stream(c.getType().getEnumConstants())
                            .map(e -> ((Enum<?>) e).name()).toList();
                    assertThat(campo.opciones()).extracting(CampoDetalle.Opcion::valor)
                            .as("opciones de %s.%s", d.modulo(), c.getName())
                            .containsExactlyElementsOf(constantes);
                } else {
                    assertThat(campo.opciones()).isEmpty();
                }
            }
        }
    }

    @Test
    void losObligatoriosSonLosDelValidador() {
        // Mismos "required" de cada rama en database/validadores/07_tickets.js.
        assertThat(obligatorios(ModuloErp.VENTAS_ONLINE)).containsExactly("pasarelaPagoId", "urlError", "navegador");
        assertThat(obligatorios(ModuloErp.PRODUCCION)).containsExactly("maquinariaId", "codigoLote", "faseProceso");
        assertThat(obligatorios(ModuloErp.INVENTARIOS))
                .containsExactly("codigoBodega", "codigoProducto", "tipoOperacion");
        assertThat(obligatorios(ModuloErp.COMPRAS)).containsExactly("nitProveedor", "etapa");
        assertThat(obligatorios(ModuloErp.VENTAS_EN_SITIO)).containsExactly("cajaId", "turnoId", "dispositivo");
    }

    private static List<String> obligatorios(ModuloErp modulo) {
        return CatalogoDetalle.de(modulo).campos().stream()
                .filter(CampoDetalle::obligatorio).map(CampoDetalle::nombre).toList();
    }
}
