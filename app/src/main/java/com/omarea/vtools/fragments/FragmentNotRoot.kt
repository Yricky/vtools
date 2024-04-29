package com.omarea.vtools.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.omarea.permissions.CheckRootStatus
import com.omarea.vtools.R

class FragmentNotRoot : androidx.fragment.app.Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_not_root, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<Button>(R.id.btn_retry).setOnClickListener {
            CheckRootStatus(this.context!!, Runnable {
                if (this.activity != null) {
                    this.activity!!.recreate()
                }
            }, false, null).forceGetRoot()
        }
    }

    companion object {
        fun createPage(): androidx.fragment.app.Fragment {
            val fragment = FragmentNotRoot()
            return fragment
        }
    }
}
