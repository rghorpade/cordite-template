package cordite

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

    val rpcClientA = DglRpcClient(PARTY_A_ADDRESS)
    val rpcClientB = DglRpcClient(PARTY_B_ADDRESS)

    rpcClientA.createAccount(randomAccountA)
    rpcClientA.createToken(randomSymbol, 0)
    rpcClientA.issueToken(randomSymbol, BigDecimal(100), randomAccountA)
    val tokens = rpcClientA.getTokens(randomAccountA, randomSymbol)
    println("Party A has ${tokens.sumBy { it.amount.quantity.toInt() }} tokens of type $randomSymbol in account $randomAccountA.")

    rpcClientA.transferToken(randomSymbol, BigDecimal(75), randomAccountA, randomAccountB, PARTY_B_NAME)
    val tokens2 = rpcClientA.getTokens(randomAccountA, randomSymbol)
    println("Party A has ${tokens2.sumBy { it.amount.quantity.toInt() }} tokens of type $randomSymbol in account $randomAccountA.")
    val tokens3 = rpcClientB.getTokens(randomAccountB, randomSymbol)
    println("Party B has ${tokens3.sumBy { it.amount.quantity.toInt() }} tokens of type $randomSymbol in account $randomAccountB.")

    rpcClientA.close()
    rpcClientB.close()
}

private class DglRpcClient(address: String) {
    private val rpcConnection: CordaRPCConnection
    private val rpcOps: CordaRPCOps
    private val party: Party
    private val notary: Party

    companion object {
        private val logger = loggerFor<DglRpcClient>()
    }

    init {
        val client = CordaRPCClient(NetworkHostAndPort.parse(address))
        rpcConnection = client.start(RPC_USERNAME, RPC_PASSWORD)
        rpcOps = rpcConnection.proxy
        party = rpcOps.nodeInfo().legalIdentities.single()
        notary = rpcOps.notaryPartyFromX500Name(NOTARY_NAME) ?: throw IllegalArgumentException("Notary not found.")
    }

    fun createAccount(accountId: String) {
        val accountRequests = listOf(CreateAccountFlow.Request(accountId))
        try {
            rpcOps.startFlow(::CreateAccountFlow, accountRequests, notary).returnValue.getOrThrow()
        } catch (re: RuntimeException) {
            logger.error("The account $accountId already exists.")
        }
    }

    fun createToken(symbol: String, exponent: Int) {
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
        return rpcOps.vaultQueryBy<Token.State>(accountIdCriteria.and(symbolCriteria)).states.map { it.state.data }
    }

    fun close() {
        rpcConnection.notifyServerAndClose()
    }
}