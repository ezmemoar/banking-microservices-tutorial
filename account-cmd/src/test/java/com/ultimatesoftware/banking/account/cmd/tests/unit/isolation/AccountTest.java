package com.ultimatesoftware.banking.account.cmd.tests.unit.isolation;

import com.ultimatesoftware.banking.account.cmd.aggregates.Account;
import com.ultimatesoftware.banking.account.cmd.commands.CreditAccountCommand;
import com.ultimatesoftware.banking.account.cmd.commands.DebitAccountCommand;
import com.ultimatesoftware.banking.account.cmd.commands.DeleteAccountCommand;
import com.ultimatesoftware.banking.account.cmd.commands.UpdateAccountCommand;
import com.ultimatesoftware.banking.account.cmd.exceptions.AccountNotEligibleForCreditException;
import com.ultimatesoftware.banking.account.cmd.exceptions.AccountNotEligibleForDebitException;
import com.ultimatesoftware.banking.account.cmd.exceptions.AccountNotEligibleForDeleteException;
import com.ultimatesoftware.banking.account.cmd.rules.AccountRules;
import com.ultimatesoftware.banking.events.*;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AccountTest {
    @Spy
    private Account account;

    @Mock
    private AccountRules accountRules;

    private static final ObjectId id = ObjectId.get();
    private static final String customerId = ObjectId.get().toHexString();

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        account.setAccountRules(accountRules);
    }

    @Test
    public void givenAccountIsEligibleForDelete_WhenDeleting_DeletedEventEmitted() throws Exception {
        // arrange
        doNothing().when(account).applyEvent(any());

        account.on(AccountCreatedEvent.builder()
            .id(id.toHexString())
            .customerId(customerId)
            .balance(0.0)
            .build());
        doReturn(true).when(accountRules).eligibleForDelete(any());

        // act
        account.on(new DeleteAccountCommand(id.toHexString()));

        // assert
        verify(account, times(1)).applyEvent(any());
    }

    @Test
    public void givenAccountIsNotEligibleForDelete_WhenDeleting_DeletedEventEmitted() throws Exception {
        // arrange
        account.on(AccountCreatedEvent.builder()
            .id(id.toHexString())
            .customerId(customerId)
            .balance(50.0)
            .build());
        doReturn(false).when(accountRules).eligibleForDelete(any());

        // act
        Assertions.assertThrows(AccountNotEligibleForDeleteException.class, () -> {
            account.on(new DeleteAccountCommand(id.toHexString()));
        });
    }

    @Test
    public void givenAccountEligibleForDebit_WhenDebiting_AccountDebitedEventEmitted() throws Exception {
        // arrange
        doNothing().when(account).applyEvent(any());
        account.on(AccountCreatedEvent.builder()
            .id(id.toHexString())
            .customerId(customerId)
            .balance(50.0)
            .build());
        doReturn(true).when(accountRules).eligibleForDebit(eq(account), anyDouble());

        // act
        account.on(new DebitAccountCommand(id.toHexString(), 49.0, "test"));

        // assert
        verify(account, times(1)).applyEvent(any());
    }

    @Test
    public void givenAccountInEligibleForDebit_WhenDebiting_TransactionFailedEventEmitted() throws Exception {
        // arrange
        doNothing().when(account).applyEvent(any());
        account.on(AccountCreatedEvent.builder()
            .id(id.toHexString())
            .customerId(customerId)
            .balance(49.0)
            .build());
        doReturn(false).when(accountRules).eligibleForDebit(any(), anyDouble());

        // act
        Assertions.assertThrows(AccountNotEligibleForDebitException.class, () -> {
            account.on(new DebitAccountCommand(id.toHexString(), 50.0, "test"));
        });
    }

    @Test
    public void givenAccountInEligibleForCredit_WhenCrediting_TransactionFailedIsEmitted() throws Exception {
        // arrange
        doNothing().when(account).applyEvent(any());
        account.on(AccountCreatedEvent.builder()
            .id(id.toHexString())
            .customerId(customerId)
            .balance(Double.MAX_VALUE)
            .build());
        doReturn(false).when(accountRules).eligibleForCredit(any(), anyDouble());

        // act
        Assertions.assertThrows(AccountNotEligibleForCreditException.class, () -> {
            account.on(new CreditAccountCommand(id.toHexString(), 1.0, "test"));
        });
    }

    @Test
    public void givenAccountEligibleForCredit_WhenCrediting_AccountCreditedIsEmitted() throws Exception {
        // arrange
        doNothing().when(account).applyEvent(any());
        account.on(AccountCreatedEvent.builder()
            .id(id.toHexString())
            .customerId(customerId)
            .balance(Double.MAX_VALUE - 1.0)
            .build());
        doReturn(true).when(accountRules).eligibleForCredit(any(), anyDouble());

        // act
        account.on(new CreditAccountCommand(id.toHexString(), 2.0, "test"));

        // assert
        verify(account, times(1)).applyEvent(any());
    }

    @Test
    public void givenAccountExists_WhenUpdating_AccountUpdatedIsEmitted() throws Exception {
        // arrange
        doNothing().when(account).applyEvent(any());
        account.on(AccountCreatedEvent.builder()
            .id(id.toHexString())
            .customerId(customerId)
            .balance(0.0)
            .build());

        // act
        account.on(new UpdateAccountCommand(id.toHexString(), customerId));

        // assert
        verify(account, times(1)).applyEvent(any());
    }


    @Test
    public void givenAcountCreatedEmitted_whenHandling_ThenUpdateIdBalanceCustomerId() throws Exception {
        // arrange
        String customerId = UUID.randomUUID().toString();

        // act
        account.on(AccountCreatedEvent.builder()
            .id(id.toHexString())
            .customerId(customerId)
            .balance(0.0)
            .build());

        // assert
        assertEquals(BigDecimal.valueOf(0.0), account.getBalance());
        assertEquals(id, account.getId());
        assertEquals(customerId, account.getCustomerId());
    }

    @Test
    public void givenAcountDebitedEmitted_whenHandling_ThenUpdateBalance() throws Exception {
        // arrange
        String customerId = UUID.randomUUID().toString();
        account.on(AccountCreatedEvent.builder()
            .id(id.toHexString())
            .customerId(customerId)
            .balance(0.0)
            .build());

        // act
        account.on(AccountDebitedEvent.builder()
            .id(id.toHexString())
            .customerId(customerId)
            .debitAmount(10.0)
            .balance(10.0)
            .transactionId("test")
            .build());

        // assert
        assertEquals(BigDecimal.valueOf(10.0), account.getBalance());
    }

    @Test
    public void givenAcountCreditedEmitted_whenHandling_ThenUpdateBalance() throws Exception {
        // arrange
        String customerId = UUID.randomUUID().toString();
        account.on(AccountCreatedEvent.builder()
            .id(id.toHexString())
            .customerId(customerId)
            .balance(0.0)
            .build());

        // act
        account.on(AccountCreditedEvent.builder()
            .id(id.toHexString())
            .customerId(customerId)
            .creditAmount(10.0)
            .balance(10.0)
            .transactionId("test")
            .build());

        // assert
        assertEquals(BigDecimal.valueOf(10.0), account.getBalance());
    }

    @Test
    public void givenAcountDeletedEmitted_whenHandling_ThenMarkDeleted() throws Exception {
        // arrange
        doNothing().when(account).delete();
        account.on(AccountCreatedEvent.builder()
            .id(id.toHexString())
            .customerId(customerId)
            .balance(0.0)
            .build());

        // act
        account.on(AccountDeletedEvent.builder()
            .id(id.toHexString())
            .build());

        // assert
        verify(account, times(1)).delete();
    }

    @Test
    public void givenAcountUpdatedEmitted_whenHandling_ThenUpdateCustomerId() throws Exception {
        // arrange
        String customerId = UUID.randomUUID().toString();
        account.on(AccountCreatedEvent.builder()
            .id(id.toHexString())
            .customerId(customerId)
            .balance(20.0)
            .build());

        // act
        account.on(AccountUpdatedEvent.builder()
            .id(id.toHexString())
            .customerId(customerId)
            .build());

        // assert
        assertEquals(customerId, account.getCustomerId());
    }
}