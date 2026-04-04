# 4. Límites de responsabilidad (Domain / Application / Infrastructure)

Reglas estrictas de separación (debe / no debe):

Domain (clase de responsabilidad):

- Debe contener: reglas de negocio puras, invariantes, value objects, exceptions de dominio y contratos (puertos) que expresan lo que el dominio necesita.
- No debe: conocer detalles de almacenamiento, formatos XML, S3 keys, transacciones o lógica de transporte.

Application (orquestación de casos de uso):

- Debe contener: orquestadores que implementan el flujo (ej: `EmitirFactura33UseCase`), coordinación de puertos, manejo de políticas de retry y decisiones de negocio que combinan varios puertos.
- Debe mapear errores de dominio a estados y registrar eventos en `processing_history`.
- No debe: contener código de bajo nivel de acceso a BD, clientes HTTP o SDKs S3; esas responsabilidades son de `infrastructure`.

Infrastructure (adaptadores concretos):

- Debe contener: implementaciones de `DocumentoRepositoryPort`, `StoragePort`, `FolioPort`, `SignerPort`, `QueuePort` y mappers a entidades JPA.
- Debe ofrecer transacciones y mecanismos de persistencia atómica donde proceda.
- No debe: contener reglas de negocio ni invariantes complejas; solo transformaciones y llamadas externas.

Interfaces (exposición externa):

- Debe contener: controladores REST, DTOs de entrada/salida, validaciones HTTP y mapeos hacia objetos de `application`.
- No debe: contener lógica de negocio; solo validación y mapping.

Reglas adicionales (solo puede / debe):

- Solo `application` puede invocar múltiples puertos y decidir sobre compensaciones o reintentos.
- Solo `infrastructure` puede acceder a credenciales y provider specifics; `application` debe recibir abstracciones.
- Si se requiere una operación atómica que toque BD y storage, la coordinación debe usar protocolo de two-phase commit lógico: persistir estado provisional en BD y confirmar tras storage exitoso; evitar bloqueos DB prolongados.
