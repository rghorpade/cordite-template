package cordite.rpc

import io.cordite.dgl.corda.account.AccountAddress
import io.cordite.dgl.corda.account.CreateAccountFlow
import io.cordite.dgl.corda.token.CreateTokenTypeFlow
import io.cordite.dgl.corda.token.Token
import io.cordite.dgl.corda.token.Token.TokenSchemaV1.PersistedToken
import io.cordite.dgl.corda.token.TokenType
import io.cordite.dgl.corda.token.TokenType.TokenTypeSchemaV1.PersistedTokenType
import io.cordite.dgl.corda.token.flows.IssueTokenFlow
import io.cordite.dgl.corda.token.flows.TransferTokenFlow
import io.cordite.dgl.corda.token.issuedBy
import net.corda.core.contracts.Amount
import net.corda.core.contracts.withoutIssuer
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.QueryCriteria.VaultCustomQueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import java.math.BigDecimal
import java.security.InvalidParameterException
import java.util.*

private const val PARTY_A_ADDRESS = "localhost:10006"
private const val PARTY_B_ADDRESS = "localhost:10009"
private val PARTY_A_NAME = CordaX500Name.parse("O=PartyA,L=London,C=GB")
private val PARTY_B_NAME = CordaX500Name.parse("O=PartyB,L=New York,C=US")
private const val RPC_USERNAME = "user1"
private const val RPC_PASSWORD = "test"
private val NOTARY_NAME = CordaX500Name("Notary", "London", "GB")

fun main(args: Array<String>) {
    val (randomAccountA, randomAccountB, randomSymbol) = (0..2).map { UUID.randomUUID().toString() }

    val clientA = DglClientRpc(PARTY_A_ADDRESS, RPC_USERNAME, RPC_PASSWORD, NOTARY_NAME)
    val clientB = DglClientRpc(PARTY_B_ADDRESS, RPC_USERNAME, RPC_PASSWORD, NOTARY_NAME)

    // PartyA issues itself 100 of a new token.
    clientA.createAccount(randomAccountA)
    clientA.createTokenType(randomSymbol, 0)
    clientA.issueToken(randomSymbol, BigDecimal(100), randomAccountA)

    // PartyA transfers 75 of the new token to PartyB.
    clientA.transferToken(randomSymbol, BigDecimal(75), randomAccountA, randomAccountB, PARTY_B_NAME)

    // We look at the output. PartyA has 25 and PartyB has 75.
    println(clientA.balanceForAccount(randomAccountA))
    println(clientB.balanceForAccount(randomAccountB))

    clientA.close()
    clientB.close()
}

private class DglClientRpc(address: String, username: String, password: String, notaryName: CordaX500Name) : ClientRpc(address, username, password) {
    private val notary = rpcOps.notaryPartyFromX500Name(notaryName) ?: throw IllegalArgumentException("Notary not found.")

    companion object {
        private val logger = loggerFor<DglClientRpc>()
    }

    fun createAccount(accountId: String) {
        val accountRequests = listOf(CreateAccountFlow.Request(accountId))
        rpcOps.startFlow(::CreateAccountFlow, accountRequests, notary).returnValue.getOrThrow()
    }

    fun createTokenType(symbol: String, exponent: Int) {
        rpcOps.startFlow(::CreateTokenTypeFlow, symbol, exponent, notary).returnValue.getOrThrow()
    }

    fun issueToken(symbol: String, amount: BigDecimal, accountId: String) {
        val criteria = VaultCustomQueryCriteria(PersistedTokenType::symbol.equal(symbol))
        val descriptor = rpcOps.vaultQueryBy<TokenType.State>(criteria).states.firstOrNull()?.state?.data?.descriptor
                ?: throw InvalidParameterException("Unknown token type $symbol.")
        val amountTokenType = Amount.fromDecimal(amount, descriptor)
        val tokenIssuance = Token.generateIssuance(amountTokenType.issuedBy(party.ref(1)), accountId, party)
        rpcOps.startFlow(::IssueTokenFlow, tokenIssuance, notary, "").returnValue.getOrThrow()
    }

    fun transferToken(symbol: String, amount: BigDecimal, fromAccountId: String, toAccountId: String, toPartyName: CordaX500Name) {
        val criteria = VaultCustomQueryCriteria(PersistedTokenType::symbol.equal(symbol))
        val descriptor = rpcOps.vaultQueryBy<TokenType.State>(criteria).states.firstOrNull()?.state?.data?.descriptor
                ?: throw InvalidParameterException("Unknown token type $symbol.")
        val amountTokenType = Amount.fromDecimal(amount, descriptor)

        val fromAccount = AccountAddress(fromAccountId, party.name)
        val toAccount = AccountAddress(toAccountId, toPartyName)

        rpcOps.startFlow(::TransferTokenFlow, fromAccount, toAccount, amountTokenType, "", notary).returnValue.getOrThrow()
    }

    fun balanceForAccount(accountId: String): Set<Amount<TokenType.Descriptor>> {
        val accountIdCriteria = VaultCustomQueryCriteria(PersistedToken::accountId.equal(accountId))
        val states = rpcOps.vaultQueryBy<Token.State>(accountIdCriteria).states.map { it.state.data }

        val balances = mutableMapOf<TokenType.Descriptor, Amount<TokenType.Descriptor>>()
        states.forEach { state ->
            val amount = state.amount.withoutIssuer()
            balances[amount.token] = balances.getOrDefault(amount.token, Amount(0, amount.token)) + amount
        }

        return balances.values.toSet()
    }
}