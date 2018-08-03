package cordite.rpc

import io.bluebank.braid.core.async.getOrThrow
import io.cordite.dao.MemberProposal
import io.cordite.dao.Proposal
import io.cordite.dao.flows.CreateDaoFlow
import io.cordite.dao.flows.CreateSponsorAction
import io.cordite.dao.flows.SponsorAction
import io.cordite.dao.flows.SponsorProposalFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import java.util.*

private const val PARTY_A_ADDRESS = "localhost:10006"
private const val PARTY_B_ADDRESS = "localhost:10009"
private val PARTY_A_NAME = CordaX500Name.parse("O=PartyA,L=London,C=GB")
private val PARTY_B_NAME = CordaX500Name.parse("O=PartyB,L=New York,C=US")
private const val RPC_USERNAME = "user1"
private const val RPC_PASSWORD = "test"
private val NOTARY_NAME = CordaX500Name("Notary", "London", "GB")

fun main(args: Array<String>) {
    val daoName = UUID.randomUUID().toString()

    val clientA = DaoClientRpc(PARTY_A_ADDRESS, RPC_USERNAME, RPC_PASSWORD, NOTARY_NAME)
    val clientB = DaoClientRpc(PARTY_B_ADDRESS, RPC_USERNAME, RPC_PASSWORD, NOTARY_NAME)

    // PartyA creates a DAO.
    val daoState = clientA.createDao(daoName)

    // PartyB joins the DAO.
    val proposalState = clientB.createNewMemberProposal(daoName, PARTY_A_NAME)
    // TODO: Explain why B can accept its own membership proposal.
//    clientB.dao.acceptNewMemberProposal(proposalState.proposal.proposalKey, partyA).getOrThrow()
//    val updatedDaoState = clientA.dao.daoFor(daoState.daoKey)
//    println(updatedDaoState.members)

    clientA.close()
    clientB.close()
}

private class DaoClientRpc(address: String, username: String, password: String, notaryName: CordaX500Name) : ClientRpc(address, username, password) {
    private val notary = rpcOps.notaryPartyFromX500Name(notaryName) ?: throw IllegalArgumentException("Notary not found.")

    companion object {
        private val logger = loggerFor<DaoClientRpc>()
    }

    fun createDao(daoName: String) {
        rpcOps.startFlow(::CreateDaoFlow, daoName, notary).returnValue.getOrThrow()
    }

    fun createNewMemberProposal(daoName: String, sponsorName: CordaX500Name) {
        val proposal = MemberProposal(party, daoName)
        val sponsorAction = CreateSponsorAction(proposal, daoName)
        val sponsor = rpcOps.wellKnownPartyFromX500Name(sponsorName) ?: throw IllegalArgumentException("Sponsor not found.")
        rpcOps.startFlowDynamic(SponsorProposalFlow::class.java, sponsorAction, sponsor).returnValue.getOrThrow()
    }
}