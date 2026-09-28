# Protocolo de Comunicação

O sistema utiliza mensagens de texto codificadas em UTF-8. Cada mensagem TCP ocupa uma linha.

Internamente, os campos são separados pelo caractere `|`. Porém, o usuário pode digitar comandos mais simples, como `reservar 5`, porque o cliente converte automaticamente a entrada para o formato interno do protocolo.

## Comandos digitados pelo usuário

| Comando | Função |
|---|---|
| `listar` | Exibe os assentos disponíveis |
| `reservar 5` | Solicita a reserva do assento 5 |
| `cancelar 5` | Cancela a reserva do assento 5 |
| `reservados` | Exibe os assentos reservados |
| `status` | Consulta o estado do servidor pela conexão TCP |
| `sair` | Encerra o cliente |

## Comandos TCP transmitidos

| Comando transmitido | Função | Exemplo de resposta |
|---|---|---|
| <code>LISTAR</code> | Lista os assentos disponíveis | <code>LISTA&#124;1,2,3,4,5</code> |
| <code>RESERVAR&#124;assento&#124;usuario</code> | Solicita a reserva de um assento | <code>OK&#124;assento 5 reservado para guilherme</code> |
| <code>CANCELAR&#124;assento&#124;usuario</code> | Cancela uma reserva do próprio usuário | <code>OK&#124;reserva do assento 5 cancelada</code> |
| <code>RESERVADOS</code> | Lista os assentos reservados e seus usuários | <code>RESERVADOS&#124;5:guilherme</code> |
| <code>STATUS</code> | Consulta o estado do servidor pela conexão TCP | <code>STATUS&#124;REPLICA=A&#124;PAPEL=PRIMARIA</code> |
| <code>SAIR</code> | Encerra a conexão TCP | <code>BYE</code> |

## Exemplo de conversão feita pelo cliente

Quando o usuário digita:

```text
reservar 5
```

O cliente envia ao servidor:

```text
RESERVAR|5|guilherme
```

Dessa forma, o usuário utiliza comandos mais fáceis, enquanto a comunicação interna mantém um formato padronizado.

## Comandos internos de replicação

Os comandos abaixo são utilizados somente na comunicação entre as réplicas. O usuário não precisa digitá-los.

| Comando interno | Função |
|---|---|
| <code>REPLICAR_RESERVA&#124;assento&#124;usuario</code> | Copia uma reserva da réplica primária para a réplica de backup |
| <code>REPLICAR_CANCELAMENTO&#124;assento&#124;usuario</code> | Copia um cancelamento da réplica primária para a réplica de backup |

### Exemplo de replicação de reserva

```text
REPLICAR_RESERVA|5|guilherme
```

### Exemplo de replicação de cancelamento

```text
REPLICAR_CANCELAMENTO|5|guilherme
```

## Comunicação UDP

A consulta rápida do estado do servidor utiliza UDP.

### Solicitação enviada

```text
STATUS
```

### Exemplo de resposta

```text
STATUS|REPLICA=A|PAPEL=PRIMARIA|SINCRONIZACAO=ATIVA|RESERVAS=3|ATIVO=true
```

A resposta informa:

- `REPLICA`: identificação da réplica que respondeu;
- `PAPEL`: indica se a réplica é primária, backup ou primária promovida;
- `SINCRONIZACAO`: informa se a sincronização está ativa;
- `RESERVAS`: quantidade de assentos reservados;
- `ATIVO`: informa se o servidor está em funcionamento.

## Respostas de sucesso

As operações realizadas corretamente começam com `OK`.

Exemplos:

```text
OK|assento 5 reservado para guilherme
```

```text
OK|reserva do assento 5 cancelada
```

Quando o cliente encerra a conexão, o servidor responde:

```text
BYE
```

## Respostas de erro

As operações que não podem ser realizadas começam com `ERRO`.

| Resposta | Significado |
|---|---|
| <code>ERRO&#124;assento inexistente</code> | O número informado não corresponde a um assento existente |
| <code>ERRO&#124;assento ja reservado por usuario</code> | O assento já foi reservado por outro usuário |
| <code>ERRO&#124;assento nao esta reservado</code> | Foi solicitado o cancelamento de um assento que está livre |
| <code>ERRO&#124;reserva pertence a outro usuario</code> | Um usuário tentou cancelar a reserva de outra pessoa |
| <code>ERRO&#124;numero do assento invalido</code> | O valor informado para o assento não é um número válido |
| <code>ERRO&#124;comando desconhecido</code> | O comando recebido não existe no protocolo |

## Funcionamento da replicação

O sistema utiliza o modelo primária-backup.

A réplica primária recebe as solicitações dos clientes, realiza a operação e envia a alteração para a réplica de backup. Dessa forma, as duas réplicas mantêm uma cópia das reservas.

Quando a réplica primária deixa de funcionar, o cliente tenta se conectar à réplica de backup. Ao receber uma solicitação normal de cliente, o backup é promovido e passa a funcionar como réplica primária.

## Exemplo completo de comunicação

Usuário digita:

```text
reservar 5
```

Cliente envia para a réplica primária:

```text
RESERVAR|5|guilherme
```

A réplica primária responde:

```text
OK|assento 5 reservado para guilherme
```

A réplica primária envia ao backup:

```text
REPLICAR_RESERVA|5|guilherme
```

A réplica de backup confirma:

```text
OK|replicacao realizada
```