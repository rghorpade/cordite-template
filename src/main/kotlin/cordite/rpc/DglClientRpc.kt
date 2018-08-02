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
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.QueryCriteria.VaultCustomQueryCriteria
import net.corda.core.utilities.NetworkHostAndPort
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

    clientA.createAccount(randomAccountA)
    clientA.createTokenType(randomSymbol, 0)
    clientA.issueToken(randomSymbol, BigDecimal(100), randomAccountA)
    println(clientA.getTokens(randomAccountA, randomSymbol))

    clientA.transferToken(randomSymbol, BigDecimal(75), randomAccountA, randomAccountB, PARTY_B_NAME)
    clientA.getTokens(randomAccountA, randomSymbol)
    clientB.getTokens(randomAccountB, randomSymbol)

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
        try {
            rpcOps.startFlow(::CreateAccountFlow, accountRequests, notary).returnValue.getOrThrow()
        } catch (re: RuntimeException) {
            logger.error("The account $accountId already exists.")
        }
    }

    fun createTokenType(symbol: String, exponent: Int) {
        try {
            rpcOps.startFlow(::CreateTokenTypeFlow, symbol, exponent, notary).returnValue.getOrThrow()
        } catch (re: RuntimeException) {
            logger.error("The token type $symbol already exists.")
        }
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

    fun getTokens(accountId: String, symbol: String): List<Token.State> {
        val accountIdCriteria = VaultCustomQueryCriteria(PersistedToken::accountId.equal(accountId))
        val symbolCriteria = VaultCustomQueryCriteria(PersistedToken::tokenTypeSymbol.equal(symbol))
        val tokens = rpcOps.vaultQueryBy<Token.State>(accountIdCriteria.and(symbolCriteria)).states.map { it.state.data }
        val totalValue = tokens.sumBy { it.amount.quantity.toInt() }
        println("$organisation has $totalValue tokens of type $symbol in account $accountId.")
        return tokens
    }
}