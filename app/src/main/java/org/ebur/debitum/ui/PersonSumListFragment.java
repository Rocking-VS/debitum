package org.ebur.debitum.ui;

import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialFadeThrough;

import org.ebur.debitum.R;
import org.ebur.debitum.database.Person;
import org.ebur.debitum.database.PersonWithTransactions;
import org.ebur.debitum.database.Transaction;
import org.ebur.debitum.viewModel.PersonSumListViewModel;

import java.util.concurrent.ExecutionException;

public class PersonSumListFragment extends Fragment {

    private static final String TAG = "PersonSumListFragment";

    private PersonSumListViewModel viewModel;
    private RecyclerView recyclerView;
    private PersonSumListAdapter adapter;
    private SelectionTracker<Long> selectionTracker = null;
    private View emptyView;

    private int nRowsSelected = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Transitions
        setEnterTransition(new MaterialFadeThrough().setDuration(400));
        setExitTransition(new MaterialFadeThrough().setDuration(400));
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(PersonSumListViewModel.class);

        View root = inflater.inflate(R.layout.fragment_person_sum_list, container, false);

        emptyView = root.findViewById(R.id.emptyDbView);

        setupTotalHeader(root);
        setupRecyclerView(root);
        buildSelectionTracker();
        subscribeToViewModel();
        setHasOptionsMenu(true);
        return root;
    }

    private void setupRecyclerView(View root) {
        recyclerView = root.findViewById(R.id.recyclerview);
        adapter = new PersonSumListAdapter(new PersonSumListAdapter.PersonSumDiff());
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireActivity()));
        DividerItemDecoration decoration = new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL);
        InsetDrawable divider = (InsetDrawable) ResourcesCompat.getDrawable(getResources(), R.drawable.divider_inset_start, null);
        assert divider!=null;
        divider.setAlpha(33);
        decoration.setDrawable(divider);
        recyclerView.addItemDecoration(decoration);
    }

    private void subscribeToViewModel() {
        viewModel.getPersonsWithTransactions().observe(getViewLifecycleOwner(), pwtList -> {
            updateTotalHeader(
                    PersonWithTransactions.getSum(pwtList),
                    PersonWithTransactions.getNumberOfItems(pwtList)
            );
            adapter.submitList(pwtList);
            if(pwtList.isEmpty()) {
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
            } else {
                recyclerView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
            }
        });
    }

    private void setupTotalHeader(View root) {
        TextView descView = root.findViewById(R.id.header_description);
        descView.setVisibility(View.INVISIBLE);
    }

    protected void updateTotalHeader(int totalMoney, int totalItems) {
        TextView totalView = requireView().findViewById(R.id.header_total);
        totalView.setText(Transaction.formatMonetaryAmount(totalMoney));
        int totalColor = totalMoney>0 ? R.color.owe_green : R.color.lent_red;
        totalView.setTextColor(totalView.getResources().getColor(totalColor, null));
    }

    private void buildSelectionTracker() {
        selectionTracker = new SelectionTracker.Builder<>(
                "personSumListSelection",
                recyclerView,
                new ItemKeyProvider<Long>(ItemKeyProvider.SCOPE_MAPPED) {
                    @Override
                    public Long getKey(int position) {
                        return adapter.getItemId(position);
                    }

                    @Override
                    public int getPosition(@NonNull Long key) {
                        RecyclerView.ViewHolder viewHolder = recyclerView.findViewHolderForItemId(key);
                        return viewHolder == null ? RecyclerView.NO_POSITION : viewHolder.getLayoutPosition();
                    }
                },
                new ListItemDetailsLookup(recyclerView),
                StorageStrategy.createLongStorage())
                .withSelectionPredicate(SelectionPredicates.createSelectAnything())
                .build();

        // change visible menu items depending on item selection
        this.selectionTracker.addObserver(new SelectionTracker.SelectionObserver<Long>() {
            @Override
            public void onSelectionChanged() {
                super.onSelectionChanged();
                invalidateMenuIfNeeded(selectionTracker.getSelection().size());
            }
        });
        adapter.setSelectionTracker(this.selectionTracker);
    }

    @Override
    public void onResume() {
        super.onResume();
        // show edit/delete transaction buttons based on number of selected items
        invalidateMenuIfNeeded(selectionTracker.getSelection().size());
    }

    // ---------------------------
    // Toolbar Menu event handling
    // ---------------------------

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_person_sum_list, menu);

        // only show add person menu item when nothing is selected
        if(nRowsSelected > 0) menu.findItem(R.id.miAddPerson).setVisible(false);

        // only show edit transaction menu item if exactly one transaction is selected
        if(nRowsSelected != 1) menu.findItem(R.id.miEditPerson).setVisible(false);

        // only show delete transaction menu item if one or more items are selected
        if(nRowsSelected == 0) menu.findItem(R.id.miDeletePerson).setVisible(false);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if(id==R.id.miAddPerson) {
            onAddPersonAction();
            return true;
        } else if(id==R.id.miEditPerson) {
            onEditPersonAction();
            return true;
        } else if(id==R.id.miDeletePerson) {
            onDeletePersonAction();
            return true;
        }
        return true;
    }

    public void onEditPersonAction() {
        // we can assume, that only one row is selected, as the menu item is hidden else
        if (selectionTracker.getSelection().size() == 1) {
            // get selected idPerson (note: PersonSumListAdapter.getItemId returns the
            // item's person id as the unique row id, so we can use that here
            int selectedId = selectionTracker.getSelection().iterator().next().intValue();
            Person selectedPerson;
            try {
                selectedPerson = viewModel.getPersonById(selectedId);
            } catch (InterruptedException | ExecutionException e) {
                Log.e(TAG, String.format("person with id %d could not be found in the database", selectedId));
                return;
            }

            // clear selection, as nothing shall be selected upon returning from EditPersonFragment
            selectionTracker.clearSelection();

            // navigate to EditTransactionFragment
            Bundle args = new Bundle();
            args.putParcelable(EditPersonFragment.ARG_EDITED_PERSON, selectedPerson);
            NavHostFragment.findNavController(this).navigate(R.id.action_editPerson, args);
        }
    }

    public void onDeletePersonAction() {
        // make copy of selection so we have a constant list of selected items, even during
        // iteratively deleting items
        MutableSelection<Long> selection = new MutableSelection<>();
        selectionTracker.copySelection(selection);

        int deleteCount = selection.size();

        // ask for confirmation
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(requireActivity());
        builder.setPositiveButton(R.string.delete_dialog_confirm, (dialog, id) -> {
            for (Long idPerson : selection) {
                // Room uses the primary key (idPerson) to find the row to be deleted, so an
                // empty Person with the correct id will suffice
                viewModel.delete(new Person(idPerson.intValue()));
            }
            selectionTracker.clearSelection();
            Snackbar.make(requireView(),
                    getResources().getQuantityString(R.plurals.person_sum_list_snackbar_deleted, deleteCount, deleteCount),
                    Snackbar.LENGTH_LONG)
                    .show();
        });
        builder.setNegativeButton(R.string.dialog_cancel, (dialog, id) -> dialog.cancel());
        builder.setMessage(R.string.person_sum_list_dialog_delete_text)
                .setTitle(getResources().getQuantityString(R.plurals.person_sum_list_dialog_delete_title, deleteCount, deleteCount));
        AlertDialog dialog = builder.create();

        dialog.show();
    }

    public void onAddPersonAction() {
        //Bundle args = new Bundle();
        //args.putParcelable(EditPersonFragment.ARG_EDITED_PERSON, null);
        NavHostFragment.findNavController(this).navigate(R.id.action_addPerson);//, args);
    }

    private void invalidateMenuIfNeeded(int nRowsSelectedNew) {
        // rebuild options menu if relevant change in selected item number occured
        if ( nRowsSelectedNew != nRowsSelected
                && (nRowsSelectedNew <= 1 || nRowsSelected <= 1)) {
            requireActivity().invalidateOptionsMenu();
        }
        nRowsSelected = nRowsSelectedNew;
    }
}