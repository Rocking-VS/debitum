package org.ebur.debitum.ui;

import androidx.annotation.NonNull;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.snackbar.Snackbar;

import org.ebur.debitum.R;
import org.ebur.debitum.database.Person;
import org.ebur.debitum.viewModel.EditPersonViewModel;

import java.util.concurrent.ExecutionException;

public class EditPersonFragment extends Fragment {

    public static final String ARG_EDITED_PERSON = "editedPerson";

    private EditPersonViewModel viewModel;
    private NavController nav;

    private EditText nameView;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(EditPersonViewModel.class);
        nav = NavHostFragment.findNavController(this);

        View root = inflater.inflate(R.layout.fragment_edit_person, container, false);
        nameView = root.findViewById(R.id.edit_person_name);

        // pre-populate name view if a Person is edited
        Person editedPerson = requireArguments().getParcelable(ARG_EDITED_PERSON);
        viewModel.setEditedPerson(editedPerson);
        if(editedPerson != null) nameView.setText(editedPerson.name);
        else ((MainActivity) requireActivity()).setToolbarTitle(R.string.title_fragment_edit_person_add);

        setHasOptionsMenu(true);

        return root;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_edit_person, menu);

        //remove delete menu item when creating new person
        if(viewModel.isNewPerson()) menu.removeItem(R.id.miDeletePerson);
    }

    // ---------------------------
    // Toolbar Menu event handling
    // ---------------------------

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if(id==R.id.miSavePerson) {
            onSavePersonAction();
            return true;
        } else if(id==R.id.miDeletePerson) {
            onDeletePersonAction();
            return true;
        } else {
            return NavigationUI.onNavDestinationSelected(item, nav)
                    || super.onOptionsItemSelected(item);
        }
    }

    public void onSavePersonAction() {
        String name;

        // check if nameView has contents
        if(TextUtils.isEmpty(nameView.getText())) {
            String errorMessage = getResources().getString(R.string.error_message_enter_name);
            Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
            return;
        }
        else name = nameView.getText().toString();

        // check if Person with that name already exists
        try {
            if(viewModel.personExists(name)) {
                String errorMessage = getResources().getString(R.string.error_message_name_exists, name);
                Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
            }
            else {
                if(viewModel.getEditedPerson() == null) {
                    // insert new person (via viewModel) and finish activity
                    viewModel.addPerson(name);
                } else {
                    Person oldPerson = viewModel.getEditedPerson();
                    oldPerson.name = name;
                    viewModel.update(oldPerson);
                }
                nav.navigateUp();
            }
        } catch (ExecutionException | InterruptedException e) {
            String errorMessage = getResources().getString(R.string.error_message_database_access, e.getLocalizedMessage());
            Toast.makeText(getContext(),  errorMessage, Toast.LENGTH_LONG).show();
        }
    }

    public void onDeletePersonAction() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setPositiveButton(R.string.delete_dialog_confirm, (dialog, id) -> {
            viewModel.delete(viewModel.getEditedPerson());
            // navigate back to PersonSumListFragment, as any views related to the deleted Person will become invalid
            nav.navigate(R.id.action_editPerson_to_personSumList_after_delete);
            Snackbar.make(requireView(),
                    getString(R.string.edit_person_snackbar_deleted_person, viewModel.getEditedPerson().name),
                    Snackbar.LENGTH_SHORT)
                    .show();
        });
        builder.setNegativeButton(R.string.delete_dialog_cancel, (dialog, id) -> dialog.cancel());

        builder.setMessage(getString(R.string.edit_person_confirm_deletion, viewModel.getEditedPerson().name))
                .setTitle(R.string.edit_person_confirm_deletion_title);
        AlertDialog dialog = builder.create();

        dialog.show();
    }
}