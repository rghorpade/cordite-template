package cordite.braid

import io.bluebank.braid.client.BraidProxyClient
import io.bluebank.braid.core.async.getOrThrow
import io.cordite.dao.DaoApi
import io.cordite.test.utils.BraidClientHelper
import io.cordite.test.utils.BraidPortHelper
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.loggerFor
import java.util.*

private const val PARTY_A_NAME = "O=PartyA,L=London,C=GB"
private const val PARTY_B_NAME = "O=PartyB,L=New York,C=US"
private val NOTARY_NAME = CordaX500Name("Notary", "London", "GB")

fun main(args: Array<String>) {
    val daoName = UUID.randomUUID().toString()

    val clientA = DaoClientBraid(PARTY_A_NAME)
    val clientB = DaoClientBraid(PARTY_B_NAME)

    // PartyA creates a DAO.
    val daoState = clientA.dao.createDao(daoName, NOTARY_NAME).getOrThrow()

    // PartyB joins the DAO.
    val partyA = daoState.members.single()
    val proposalState = clientB.dao.createNewMemberProposal(daoName, partyA).getOrThrow()
    // TODO: Explain why B can accept its own membership proposal.
    clientB.dao.acceptNewMemberProposal(proposalState.proposal.proposalKey, partyA).getOrThrow()
    val updatedDaoState = clientA.dao.daoFor(daoState.daoKey)
    println(updatedDaoState.members)

    // TODO: In 0.2.0, we cannot view accepted proposals without access to the node's vault.

    clientA.close()
    clientB.close()
}

private class DaoClientBraid(name: String) {
    private val braidClient: BraidProxyClient
    val dao: DaoApi

    companion object {
        private val logger = loggerFor<DaoClientBraid>()
    }

    init {
        val port = BraidPortHelper().portForName(name)
        braidClient = BraidClientHelper.braidClient(port, "dao")
        dao = braidClient.bind(DaoApi::class.java)
        logger.info("Connect on https://localhost:$port.")
    }

    fun close() {
        braidClient.close()
    }
}