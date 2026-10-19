# TicketRush API

API REST desenvolvida em Spring Boot para reserva e confirmação de compra de assentos sob concorrência. O objetivo do projeto é demonstrar o isolamento de camadas da arquitetura MVC nos testes automatizados utilizando Test Slices e Mockito.

## Requisitos Técnicos

- Java 25
- Spring Boot 4.1.1
- Maven
- Banco de dados H2 (em memória)
- Lombok
- JUnit 5, Mockito, AssertJ e MockMvc

## Arquitetura e Regras de Negócio

O sistema gerencia o ciclo de vida de assentos (`Seat`) com três estados: `AVAILABLE`, `RESERVED` e `SOLD`.

1. **Reserva temporária (`POST /api/seats/{seatNumber}/reserve`)**
   - Verifica se o assento existe e está com status `AVAILABLE`.
   - Altera o status para `RESERVED`, vincula ao CPF do solicitante e define um prazo de expiração de 10 minutos a partir do `java.time.Clock` injetado.
   - Utiliza controle de concorrência otimista (`@Version`) na entidade JPA. Caso duas transações concorrentes tentem atualizar a mesma versão do registro, o `OptimisticLockingFailureException` lançado pela camada de persistência é capturado na camada de serviço e convertido em `SeatConflictException` (mapeado para HTTP `409 Conflict`).

2. **Confirmação de compra (`POST /api/seats/{seatNumber}/confirm`)**
   - Verifica se o assento está reservado para o CPF informado.
   - Valida se o tempo atual não ultrapassou o limite `reservedUntil`. Caso esteja expirado, o assento retorna ao estado `AVAILABLE`, a alteração é persistida e uma `BusinessRuleException` é lançada (HTTP `422 Unprocessable Entity`), sem acionar o gateway de pagamento.
   - Aciona a interface `PaymentGateway`. Se o pagamento for recusado, o assento é liberado de volta para `AVAILABLE` e nenhuma notificação é emitida.
   - Se o pagamento for autorizado, o status muda para `SOLD` e o `NotificationService` é chamado para envio da confirmação.

3. **Tratamento de Erros (`GlobalExceptionHandler`)**
   - Utiliza `@RestControllerAdvice` e retorna respostas padronizadas na estrutura `ProblemDetail` (RFC 7807):
     - `MethodArgumentNotValidException` -> `400 Bad Request`
     - `SeatNotFoundException` -> `404 Not Found`
     - `SeatConflictException` -> `409 Conflict`
     - `BusinessRuleException` -> `422 Unprocessable Entity`

## Estratégia de Testes e Isolamento de Camadas

Em vez de inicializar o contexto completo da aplicação (`@SpringBootTest`) para todos os cenários, os testes estão divididos por camada de responsabilidade:

### 1. Camada de Serviço (`TicketBookingServiceTest`)
- **Anotação:** `@ExtendWith(MockitoExtension.class)`
- **Escopo:** Teste unitário puro, sem inicialização do contexto do Spring.
- **Dependências Mockadas (`@Mock`):** `SeatRepository`, `PaymentGateway`, `NotificationService` e `Clock`.
- **Técnicas Aplicadas:**
  - Determinismo temporal através do mock de `Clock` (`clock.instant()` e `clock.getZone()`) para validar o cálculo exato de expiração de 10 minutos e o bloqueio de reservas vencidas.
  - Captura de argumentos com `@Captor` (`ArgumentCaptor<Seat>`) para inspecionar as mutações internas de estado da entidade antes da chamada ao repositório.
  - Simulação de falha de concorrência configurando `seatRepository.save(...)` para lançar `OptimisticLockingFailureException` e verificando sua tradução para `SeatConflictException`.
  - Verificação de ausência de efeitos colaterais com `verifyNoInteractions` e `verify(..., never())` quando a reserva expira ou o pagamento é recusado.
  - Validação da ordem estrita de execução (`InOrder`) entre autorização do pagamento, persistência da venda e envio da notificação.

### 2. Camada Web MVC (`TicketBookingControllerTest`)
- **Anotação:** `@WebMvcTest(TicketBookingController.class)`
- **Escopo:** Inicializa apenas a infraestrutura web (DispatcherServlet, conversores JSON, Bean Validation e `@RestControllerAdvice`), ignorando camada de serviço e banco de dados.
- **Dependências Mockadas (`@MockitoBean`):** `TicketBookingService`.
- **Técnicas Aplicadas:**
  - Execução de requisições HTTP simuladas via `MockMvc`.
  - Validação de contrato JSON de entrada e saída (`jsonPath`).
  - Verificação de barramento na camada de entrada (`400 Bad Request` quando o CPF é inválido), comprovando com `verifyNoInteractions(ticketBookingService)` que a camada de negócio não é acionada quando a validação falha.
  - Verificação do mapeamento de exceções de domínio (`SeatConflictException` e `BusinessRuleException`) para os códigos HTTP `409` e `422`.

### 3. Camada de Persistência (`SeatRepositoryTest`)
- **Anotação:** `@DataJpaTest`
- **Escopo:** Inicializa apenas os componentes do Spring Data JPA, Hibernate e o banco em memória H2.
- **Técnicas Aplicadas:**
  - Validação da query customizada `findExpiredReservations` garantindo que apenas reservas com `reservedUntil` anterior ao horário de referência sejam retornadas.
  - Teste prático de concorrência com `@Version`: duas instâncias da mesma entidade são carregadas e desanexadas (`entityManager.detach`) do contexto de persistência com a mesma versão inicial; a primeira atualização incrementa a versão no banco, e a segunda tentativa de atualização falha lançando `OptimisticLockingFailureException`.

## Execução dos Testes

Na raiz do projeto, execute:

```bash
./mvnw test
```

No Windows PowerShell:

```powershell
.\mvnw.cmd test
```
