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
    val newMemberProposalState = clientB.dao.createNewMemberProposal(daoName, partyA).getOrThrow()
    clientB.dao.acceptNewMemberProposal(newMemberProposalState.proposal.proposalKey, partyA).getOrThrow()
    val updatedDaoState = clientA.dao.daoFor(daoState.daoKey)
    println(updatedDaoState.members)

    // PartyA creates a proposal.
    val addressProposalState = clientA.dao.createProposal(
            "Address proposal",
            "The DAO shall be headquartered in Delaware.",
            daoState.daoKey).getOrThrow()

    // PartyA tries to accept the proposal.
    try {
        clientA.dao.acceptProposal(addressProposalState.proposal.proposalKey).getOrThrow()
    } catch (e: RuntimeException) {
        println("Not enough support - 60% of members must support it!")
    }

    // PartyB supports the proposal.
    clientB.dao.voteForProposal(addressProposalState.proposal.proposalKey).getOrThrow()

    // PartyA accepts the proposal.
    clientA.dao.acceptProposal(addressProposalState.proposal.proposalKey).getOrThrow()

    // PartyA views the proposal.
    println(clientA.dao.normalProposalsFor(updatedDaoState.daoKey))

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