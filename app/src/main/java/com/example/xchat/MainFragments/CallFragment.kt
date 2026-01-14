package com.example.xchat.MainFragments

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.xchat.Adapters.CallLogAdapter
import com.example.xchat.ChatFunctions.ChatActivity
import com.example.xchat.Models.CallLog
import com.example.xchat.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class CallFragment : Fragment() {

    private lateinit var callLogsRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var adapter: CallLogAdapter
    private val callLogs = mutableListOf<CallLog>()
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_call, container, false)
        
        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        
        // Initialize views
        callLogsRecyclerView = view.findViewById(R.id.callLogsRecyclerView)
        emptyStateText = view.findViewById(R.id.emptyStateText)
        
        // Setup RecyclerView
        setupRecyclerView()
        
        // Load call logs
        loadCallLogs()
        
        return view
    }

    private fun setupRecyclerView() {
        adapter = CallLogAdapter(callLogs) { callLog ->
            // Handle call button click - start a new call
            onCallButtonClicked(callLog)
        }
        
        callLogsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        callLogsRecyclerView.adapter = adapter
    }

    private fun loadCallLogs() {
        val currentUserId = auth.currentUser?.uid ?: return
        
        firestore.collection("users")
            .document(currentUserId)
            .collection("callLogs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Toast.makeText(requireContext(), "Error loading call logs", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                
                val logs = snapshots?.documents?.mapNotNull { doc ->
                    doc.toObject(CallLog::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                
                if (logs.isEmpty()) {
                    emptyStateText.visibility = View.VISIBLE
                    callLogsRecyclerView.visibility = View.GONE
                } else {
                    emptyStateText.visibility = View.GONE
                    callLogsRecyclerView.visibility = View.VISIBLE
                    adapter.updateCallLogs(logs)
                }
            }
    }

    private fun onCallButtonClicked(callLog: CallLog) {
        // Navigate to chat activity where user can initiate a call
        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra("userId", callLog.userId)
        }
        startActivity(intent)
    }
}
