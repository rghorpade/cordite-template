package cordite.braid

import io.bluebank.braid.client.BraidProxyClient
import io.bluebank.braid.core.async.getOrThrow
import io.cordite.dgl.corda.LedgerApi
import io.cordite.test.utils.BraidClientHelper
import io.cordite.test.utils.BraidPortHelper
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.loggerFor
import java.util.*

private const val PARTY_A_NAME = "O=PartyA,L=London,C=GB"
private const val PARTY_B_NAME = "O=PartyB,L=New York,C=US"
private val NOTARY_NAME = CordaX500Name("Notary", "London", "GB")

fun main(args: Array<String>) {
    val randomSymbol = UUID.randomUUID().toString()

    val clientA = DglClientBraid(PARTY_A_NAME)
    val clientB = DglClientBraid(PARTY_B_NAME)

    // PartyA issues itself 100 of a new token.
    val accountA = clientA.ledger.createAccount(UUID.randomUUID().toString(), NOTARY_NAME).getOrThrow()
    clientA.ledger.createTokenType(randomSymbol, 0, NOTARY_NAME).getOrThrow()
    clientA.ledger.issueToken(accountA.address.accountId, "100", randomSymbol, "", NOTARY_NAME).getOrThrow()

    // PartyA transfers 75 of the new token to PartyB.
    val accountB = clientB.ledger.createAccount(UUID.randomUUID().toString(), NOTARY_NAME).getOrThrow()
    clientA.ledger.transferToken("75", randomSymbol, accountA.address.accountId, accountB.address.uri, "", NOTARY_NAME).getOrThrow()

    // We look at the output. PartyA has 25 and PartyB has 75.
    println(clientA.ledger.balanceForAccount(accountA.address.accountId).getOrThrow())
    println(clientB.ledger.balanceForAccount(accountB.address.accountId).getOrThrow())

    clientA.close()
    clientB.close()
}

private class DglClientBraid(name: String) {
    private val braidClient: BraidProxyClient
    val ledger: LedgerApi

    companion object {
        private val logger = loggerFor<DglClientBraid>()
    }

    init {
        val port = BraidPortHelper().portForName(name)
        braidClient = BraidClientHelper.braidClient(port, "ledger")
        ledger = braidClient.bind(LedgerApi::class.java)
        logger.info("Connect on https://localhost:$port.")
    }

    fun close() {
        braidClient.close()
    }
}