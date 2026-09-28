# Documentación

> The project documentation is written in Spanish, the working language of the business it models. The
> repository `README.md` gives an English overview.

Los Markdown de esta carpeta son la única fuente de verdad del proyecto. Orden de lectura recomendado:

| Documento | ID | Contenido |
|---|---|---|
| [00_Contexto_Proyecto.md](00_Contexto_Proyecto.md) | — | Problema operativo, por qué inventario, reparaciones y visitas van juntos, qué es escenario real y qué es supuesto, qué no se afirma. |
| [01_Estandares_Ingenieria.md](01_Estandares_Ingenieria.md) | ER-ENG-001 | Estándares de diseño, seguridad, integridad, concurrencia, pruebas, CI y accesibilidad. |
| [02_Reglas_Negocio.md](02_Reglas_Negocio.md) | ER-BR-001 | Invariantes de negocio con su estado de validación (`PROPOSED`/`VALIDATED`) e implementación. |
| [03_Especificacion_Funcional.md](03_Especificacion_Funcional.md) | ER-FS-001 | Estado por módulo, permisos, requisitos funcionales, contrato HTTP, pantallas y criterios de aceptación. |
| [04_Arquitectura_ADR.md](04_Arquitectura_ADR.md) | ER-ARCH-001 | Arquitectura, estructura, esquema y decisiones (ADR-001..021) con alternativas y consecuencias. |
| [05_Trazabilidad_Requisitos.md](05_Trazabilidad_Requisitos.md) | — | Reglas críticas → requisito → implementación → restricción en PostgreSQL → prueba. |
| [design-system.md](design-system.md) | ER-DS-001 | Sistema de diseño «Grafito Eléctrico»: tokens, paletas, componentes, responsive y WCAG. |
| [runbook.md](runbook.md) | — | Diagnóstico local y operaciones frecuentes. |

**Jerarquía ante contradicciones:** ER-BR > ER-FS > ER-ARCH. Cambiar una regla, endpoint, permiso, esquema o
decisión técnica exige actualizar el documento afectado y su control de cambios.
