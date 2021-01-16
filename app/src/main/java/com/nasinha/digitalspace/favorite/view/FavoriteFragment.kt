package com.nasinha.digitalspace.favorite.view

import android.app.AlertDialog
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.nasinha.digitalspace.R
import com.nasinha.digitalspace.authentication.AppUtil
import com.nasinha.digitalspace.exploration.utils.DrawerUtils.lockDrawer
import com.nasinha.digitalspace.favorite.adapter.FavoriteAdapter
import com.nasinha.digitalspace.favorite.adapter.IFavorite
import com.nasinha.digitalspace.favorite.db.AppDatabase
import com.nasinha.digitalspace.favorite.entity.FavoriteEntity
import com.nasinha.digitalspace.favorite.repository.FavoriteRepository
import com.nasinha.digitalspace.favorite.utils.FavoriteConstants
import com.nasinha.digitalspace.favorite.utils.FavoriteConstants.APP_KEY
import com.nasinha.digitalspace.favorite.utils.FavoriteConstants.DATE
import com.nasinha.digitalspace.favorite.utils.FavoriteConstants.SORT_PREFS
import com.nasinha.digitalspace.favorite.utils.FavoriteConstants.SWITCH_PREFS
import com.nasinha.digitalspace.favorite.utils.FavoriteConstants.TEXT
import com.nasinha.digitalspace.favorite.utils.FavoriteConstants.TITLE
import com.nasinha.digitalspace.favorite.utils.FavoriteConstants.TYPE
import com.nasinha.digitalspace.favorite.utils.FavoriteUtils
import com.nasinha.digitalspace.favorite.viewmodel.FavoriteViewModel
import com.nasinha.digitalspace.favorite.viewmodel.FavoriteViewModelFactory
import kotlinx.coroutines.launch


class FavoriteFragment : Fragment(), IFavorite {
    private lateinit var _view: View
    private lateinit var _favoriteViewModel: FavoriteViewModel
    private lateinit var _listRecyclerView: RecyclerView
    private lateinit var _navController: NavController
    private lateinit var _favoriteAdapter: FavoriteAdapter
    private lateinit var iFavorite: IFavorite

    private var _favoriteList = mutableListOf<FavoriteEntity>()

    val options = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.ENGLISH)
        .setTargetLanguage(TranslateLanguage.PORTUGUESE)
        .build()

    private val englishPortugueseTranslator = Translation.getClient(options)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        iFavorite = this
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_favorite, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lockDrawer(requireActivity())
        _view = view

        _navController = findNavController()

        backBtn()
        addViewModel()
        addRecyclerView()
        initialize()
    }

    private fun backBtn() {
        val btnBackView = _view.findViewById<ImageButton>(R.id.ibBackFavorite)

        btnBackView.setOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    private fun addViewModel() {
        _favoriteViewModel = ViewModelProvider(
            this,
            FavoriteViewModelFactory(
                FavoriteRepository(
                    AppDatabase.getDatabase(_view.context).favoriteDao()
                )
            )
        ).get(FavoriteViewModel::class.java)
    }

    private fun addRecyclerView() {
        _listRecyclerView = _view.findViewById(R.id.recyclerViewFavorite)
        val manager = LinearLayoutManager(_view.context)

        _favoriteAdapter = FavoriteAdapter(_favoriteList, iFavorite) {
            val bundle = bundleOf(
                FavoriteConstants.IMAGE to it.image,
                TITLE to it.title,
                TEXT to it.text,
                DATE to FavoriteUtils.dateModifier(it.date),
                TYPE to it.type
            )
            _navController.navigate(R.id.action_favoriteFragment_to_favoriteScreenFragment, bundle)
        }

        _listRecyclerView.apply {
            setHasFixedSize(true)
            layoutManager = manager
            adapter = _favoriteAdapter
        }
    }

    private fun initialize() {
        val sortBtn = _view.findViewById<CheckBox>(R.id.cbOrderFavorite)
        val prefs = _view.context.getSharedPreferences(APP_KEY, MODE_PRIVATE)
        val prefsChecked = prefs.getBoolean(SORT_PREFS, false)

        if (_favoriteList.isEmpty()) {
            val userId = AppUtil.getUserId(requireActivity().application)!!

            _favoriteViewModel.getUserWithFavorites(userId).observe(viewLifecycleOwner, {
                val favorites = it.map { userWithFavorites -> userWithFavorites.favorites[0] }
                addAllFavorites(favorites)
                sortBtnHandler(sortBtn, prefsChecked, prefs)
            })
        }
        sortBtnHandler(sortBtn, prefsChecked, prefs)
    }

    private fun addAllFavorites(list: List<FavoriteEntity>) {
        _favoriteList.addAll(list)
        _favoriteAdapter.notifyDataSetChanged()
        checkTranslationPrefs()
    }

    private fun checkTranslationPrefs() {
        val prefs =
            requireActivity().getSharedPreferences(
                APP_KEY,
                MODE_PRIVATE
            )
        val checkPrefs = prefs?.getBoolean(SWITCH_PREFS, false)

        if (checkPrefs == true) {
            _favoriteList.map {
                val index = _favoriteList.indexOf(it)

                if (!it.title.isNullOrEmpty())
                    englishPortugueseTranslator.translate(it.title!!)
                        .addOnSuccessListener { result ->
                            it.title = result
                            _favoriteAdapter.notifyItemChanged(index)
                        }.addOnFailureListener { e ->
                            it.title = it.title
                        }
            }
        }
    }

    private fun deleteOneFavoriteDb(position: Int, favorite: FavoriteEntity) {
        _favoriteViewModel.deleteFavoriteItem(
            favorite.image,
            AppUtil.getUserId(requireActivity())!!
        ).observe(viewLifecycleOwner, {
            _favoriteList.removeAt(position)
            _favoriteAdapter.notifyItemRemoved(position)
        })
    }

    override fun iFavoriteDelete(
        position: Int,
        favorite: FavoriteEntity,
        cardView: MaterialCardView
    ) {
        val alertDialog = AlertDialog.Builder(_view.context)
        alertDialog.setTitle(getString(R.string.excluir_favorito))
        alertDialog.setMessage(getString(R.string.voce_quer_mesmo))
        alertDialog.setPositiveButton(getString(R.string.sim)) { _, _ ->
            deleteOneFavoriteDb(position, favorite)
            Toast.makeText(_view.context, getString(R.string.Item_removido), Toast.LENGTH_SHORT)
                .show()
        }
        alertDialog.setNegativeButton(getString(R.string.nao)) { dialog, _ ->
            dialog.dismiss()
        }
        alertDialog.show()
    }

    override fun iFavoriteShare(favorite: FavoriteEntity) {
        lifecycleScope.launch {
            val imageBitmap = FavoriteUtils.getBitmapFromView(_view, favorite.image)
            val text = favorite.text.toString()
            activity?.let { FavoriteUtils.shareImageText(it, _view, imageBitmap, text) }
        }
    }

    private fun sortBtnHandler(sortBtn: CheckBox, prefsChecked: Boolean, prefs: SharedPreferences) {
        sortBtn.isChecked = prefsChecked

        sortCheckHandler(sortBtn.isChecked, prefs)

        sortBtn.setOnCheckedChangeListener { _, isChecked ->
            sortCheckHandler(isChecked, prefs)
        }
    }

    private fun sortCheckHandler(isChecked: Boolean, prefs: SharedPreferences) {
        if (isChecked) {
            _favoriteList.sortByDescending { FavoriteUtils.stringToDate(it.date) }
            prefs.edit().putBoolean(SORT_PREFS, isChecked).apply()
        } else {
            _favoriteList.sortBy { FavoriteUtils.stringToDate(it.date) }
            prefs.edit().putBoolean(SORT_PREFS, isChecked).apply()
        }
        _favoriteAdapter.notifyDataSetChanged()
    }

}