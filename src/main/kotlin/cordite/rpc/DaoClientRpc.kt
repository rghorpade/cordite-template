package cordite.rpc

import io.cordite.dao.*
import io.cordite.dao.flows.AcceptSponsorAction
import io.cordite.dao.flows.CreateDaoFlow
import io.cordite.dao.flows.CreateSponsorAction
import io.cordite.dao.flows.SponsorProposalFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.vault.QueryCriteria.LinearStateQueryCriteria
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
    clientB.acceptNewMemberProposal(proposalState.proposal.proposalKey, PARTY_A_NAME)
    val updatedDaoState = clientA.daoFor(daoState.daoKey)
    println(updatedDaoState.members)

    clientA.close()
    clientB.close()
}

private class DaoClientRpc(address: String, username: String, password: String, notaryName: CordaX500Name) : ClientRpc(address, username, password) {
    private val notary = rpcOps.notaryPartyFromX500Name(notaryName) ?: throw IllegalArgumentException("Notary not found.")

    companion object {
        private val logger = loggerFor<DaoClientRpc>()
    }

    fun createDao(daoName: String): DaoState {
        return rpcOps.startFlow(::CreateDaoFlow, daoName, notary).returnValue.getOrThrow()
    }

    fun createNewMemberProposal(daoName: String, sponsorName: CordaX500Name): ProposalState<MemberProposal> {
        val proposal = MemberProposal(party, daoName)
        val sponsorAction = CreateSponsorAction(proposal, daoName)
        val sponsor = rpcOps.wellKnownPartyFromX500Name(sponsorName) ?: throw IllegalArgumentException("Sponsor not found.")
        @Suppress("UNCHECKED_CAST")
        return rpcOps.startFlowDynamic(SponsorProposalFlow::class.java, sponsorAction, sponsor).returnValue.getOrThrow() as ProposalState<MemberProposal>
    }

    fun acceptNewMemberProposal(proposalKey: ProposalKey, sponsorName: CordaX500Name): ProposalState<MemberProposal> {
        val sponsorAction = AcceptSponsorAction<MemberProposal>(proposalKey)
        val sponsor = rpcOps.wellKnownPartyFromX500Name(sponsorName) ?: throw IllegalArgumentException("Sponsor not found.")
        @Suppress("UNCHECKED_CAST")
        return rpcOps.startFlowDynamic(SponsorProposalFlow::class.java, sponsorAction, sponsor).returnValue.getOrThrow() as ProposalState<MemberProposal>
    }

    fun daoFor(daoKey: DaoKey): DaoState {
        val criteria = LinearStateQueryCriteria(linearId = listOf(daoKey.uniqueIdentifier))
        val daoStates = rpcOps.vaultQueryBy<DaoState>(criteria)
        return daoStates.states.map { it.state }.first().data
    }
}