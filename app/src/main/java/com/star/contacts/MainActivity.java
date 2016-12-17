package com.star.contacts;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.star.contacts.model.Contact;
import com.star.contacts.service.UpdateContactService;
import com.star.contacts.view.MergeRecyclerAdapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";
    private RecyclerView mListView;
    private MergeRecyclerAdapter mergeRecyclerAdapter;
    private DupContactAdapter mDupContactAdapter;
    private ContactAdapter mContactAdapter;

    private HandleContactTask mContactTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mListView = (RecyclerView) findViewById(R.id.list_view);
        mergeRecyclerAdapter = new MergeRecyclerAdapter();
        mDupContactAdapter = new DupContactAdapter();
        mContactAdapter = new ContactAdapter();
        mergeRecyclerAdapter.addAdapter(mDupContactAdapter);
        mergeRecyclerAdapter.addAdapter(mContactAdapter);
        mListView.setLayoutManager(new LinearLayoutManager(this));
        mListView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        mListView.setAdapter(mergeRecyclerAdapter);

        mContactTask = new HandleContactTask();
        mContactTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }


    private boolean mIsDeleteAction = false;

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        getMenuInflater().inflate(R.menu.menu_main, menu);
        if (!mIsDeleteAction) {
            menu.removeItem(R.id.action_remove);
        } else {
            menu.removeItem(R.id.action_search);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_remove:
                handleDeleteContacts();
                break;
            case R.id.action_search:

                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleDeleteContacts() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
        alertDialogBuilder.setTitle(R.string.alert_dialog_delete_title).setMessage(R.string.alert_dialog_delete_message).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                deleteContacts();
                dialog.dismiss();
            }
        }).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        }).show();
    }

    private void deleteContacts() {
        List<Contact> contacts = mDupContactAdapter.getCheckedData();
        if (contacts.size() == 0) {
            return;
        }
        deleteMultiContract(contacts);
        UpdateContactService.updateContacts(MainActivity.this, (ArrayList<Contact>) contacts);
    }

    private void deleteContact(Contact contact) {
        final List<Contact> contacts = new ArrayList<>();
        contacts.clear();
        contacts.add(contact);
        deleteMultiContract(contacts);
    }

    private void deleteMultiContract(List<Contact> contacts) {
        showProgressDialog();
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        for (int i = 0; i < contacts.size(); i++) {
            Log.e(TAG, "deleteMultiContract contacts.dataId == " +  contacts.get(i).dataId + ", name == " + contacts.get(i).displayName);
            ops.add(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection(ContactsContract.Data._ID + "=?",
                            new String[]{contacts.get(i).dataId})
                    .build());
        }
        try {
            getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (RemoteException | OperationApplicationException e) {
            e.printStackTrace();
        }

        mContactTask = new HandleContactTask();
        mContactTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mContactTask != null) {
            mContactTask.cancel(true);
            mContactTask = null;
        }
    }

    /**
     * 用来查询联系人，把相同用户名的id放到一组，类似于{xingxing:100,139,40}这样的形式
     * @param cr
     * @return
     */
    private Map<String, String> divideContacts(ContentResolver cr) {
        Map<String, String> map = new LinkedHashMap<>();
        Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, ContactsContract.Contacts.SORT_KEY_PRIMARY);
        if (cur.getCount() > 0) {
            while (cur.moveToNext()) {
                String id = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID));
                String name = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                if (map.containsKey(name)) {
                    String values = map.get(name);
                    values += ("," + id);
                    map.put(name, values);
                } else {
                    map.put(name, id);
                }
            }
        }
        cur.close();
        return map;
    }

    private List<Contact> getContactById(ContentResolver cr, String contactId, String displayName) {
        List<Contact> contacts = new ArrayList<>();
        Cursor pCur = cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                new String[]{contactId},
                null);
        while (pCur.moveToNext()) {
            String phoneNo = pCur.getString(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
            String dataId = pCur.getString(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID));//其实是data表中的_id
            String rawContactId = pCur.getString(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID));
            Log.e(TAG, "name == " + displayName + ", phoneNo == " + phoneNo + ", dataId == " + dataId + ", contactId == " + contactId + ", rawContactId == " + rawContactId);
            contacts.add(new Contact(displayName, phoneNo, contactId, dataId, rawContactId));
        }
        pCur.close();
        return contacts;
    }

    private void generateContracts(ContentResolver cr, Map<String, String> map, List<Contact> dupContacts, List<Contact> contacts) {
        for(Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value.contains(",")) {
                String ids[] = value.split(",");
                for (String id : ids) {
                    List<Contact> subContacts = getContactById(cr, id, key);
                    if (subContacts.size() > 0) {
                        dupContacts.addAll(subContacts);
                    }
                }
            } else {
                List<Contact> subContacts = getContactById(cr, value, key);
                if (subContacts.size() > 0) {
                    if (subContacts.size() == 1) {
                        contacts.addAll(subContacts);
                    } else {
                        dupContacts.addAll(subContacts);
                    }
                }
            }
        }
    }

    private ProgressDialog mProgressDialog;

    private void showProgressDialog() {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setMessage("Loading...");
            mProgressDialog.setCancelable(false);
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.show();
        }
    }

    private void hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.cancel();
            mProgressDialog = null;
        }
    }

    private void deleteContract(String contactId) {
        showProgressDialog();
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        ops.add(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection(ContactsContract.Data._ID + "=?", new String[]{contactId})
                .build());
        try {
            getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (RemoteException | OperationApplicationException e) {
            e.printStackTrace();
        }

        mListView.postDelayed(new Runnable() {
            @Override
            public void run() {
                hideProgressDialog();
//                mAdapter.setData(getContracts(getContentResolver()));
//                mAdapter.notifyDataSetChanged();
            }
        }, 500);
    }

    public static class Contact {
        String displayName;
        String phoneNumber;
        String dataId;//代表data表里面的id

        Contact(String displayName, String phoneNumber, String dataId) {
            this.displayName = displayName;
            this.phoneNumber = phoneNumber;
            this.dataId = dataId;
        }
    }


    class DupContactAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements MergeRecyclerAdapter.OnViewTypeCheckListener {
        private static final int ITEM_HEADER = 0;
        private static final int ITEM_CONTENT = 1;
        private List<Contact> mData = new ArrayList<>();
        private List<Contact> mCheckedData = new ArrayList<>();

        public List<Contact> getCheckedData() {
            return mCheckedData;
        }

        public DupContactAdapter(List<Contact> mData) {
            this.mData = mData;
        }

        public DupContactAdapter() {
        }

        public void setData(List<Contact> data) {
            this.mData = data;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == ITEM_HEADER) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_header_1, parent, false);
                return new HeaderViewHolder(view);
            } else if (viewType == ITEM_CONTENT) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_content_1, parent, false);
                return new ContentViewHolder(view);
            }
            throw new IllegalStateException("Adapter don't have this viewType " + viewType);
        }

        private void handleMenu() {
            if (!mIsDeleteAction && mCheckedData.size() > 0) {
                mIsDeleteAction = true;
                invalidateOptionsMenu();
            } else if (mIsDeleteAction && mCheckedData.size() == 0) {
                mIsDeleteAction = false;
                invalidateOptionsMenu();
            }
        }
        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, final int position) {
            if (holder instanceof ContentViewHolder) {
                final Contact contact = mData.get(position - 1);
                ContentViewHolder contentViewHolder = (ContentViewHolder) holder;
                contentViewHolder.phoneView.setText(contact.phoneNumber);
                contentViewHolder.nameView.setText(contact.displayName);
                contentViewHolder.checkBox.setVisibility(View.VISIBLE);
                contentViewHolder.checkBox.setChecked(false);
                contentViewHolder.checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                        if (b) {
                            mCheckedData.add(contact);
                        } else {
                            mCheckedData.remove(contact);
                        }
                        handleMenu();
                    }
                });
            } else if (holder instanceof HeaderViewHolder) {
                HeaderViewHolder headerViewHolder = (HeaderViewHolder) holder;
                headerViewHolder.titleView.setText("重复联系人");
            }
        }

        @Override
        public int getItemCount() {
            return mData.size() == 0 ? 0 : mData.size() + 1;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return ITEM_HEADER;
            }
            return ITEM_CONTENT;
        }

        @Override
        public boolean checkViewType(int viewType) {
            return viewType == ITEM_HEADER || viewType == ITEM_CONTENT;
        }

        class ContentViewHolder extends RecyclerView.ViewHolder {
            TextView nameView;
            TextView phoneView;
            CheckBox checkBox;

            public ContentViewHolder(View itemView) {
                super(itemView);
                nameView = (TextView) itemView.findViewById(R.id.name);
                phoneView = (TextView) itemView.findViewById(R.id.phone);
                checkBox = (CheckBox) itemView.findViewById(R.id.checkbox);
            }
        }

        class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView titleView;
            CheckBox checkBox;

            public HeaderViewHolder(View itemView) {
                super(itemView);
                titleView = (TextView) itemView.findViewById(R.id.title);
                checkBox = (CheckBox) itemView.findViewById(R.id.checkbox);
            }
        }
    }

    class ContactAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements MergeRecyclerAdapter.OnViewTypeCheckListener {
        private static final int ITEM_HEADER = 2;
        private static final int ITEM_CONTENT = 3;
        private List<Contact> mData = new ArrayList<>();

        public ContactAdapter(List<Contact> mData) {
            this.mData = mData;
        }

        public ContactAdapter() {
        }

        public void setData(List<Contact> data) {
            this.mData = data;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == ITEM_HEADER) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_header_1, parent, false);
                return new HeaderViewHolder(view);
            } else if (viewType == ITEM_CONTENT) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_content_1, parent, false);
                return new ContentViewHolder(view);
            }
            throw new IllegalStateException("Adapter don't have this viewType " + viewType);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof ContentViewHolder) {
                Contact contact = mData.get(position - 1);
                ContentViewHolder contentViewHolder = (ContentViewHolder) holder;
                contentViewHolder.phoneView.setText(contact.phoneNumber);
                contentViewHolder.nameView.setText(contact.displayName);
            }
            if (holder instanceof HeaderViewHolder) {
                HeaderViewHolder headerViewHolder = (HeaderViewHolder) holder;
                headerViewHolder.titleView.setText("联系人");
            }
        }

        @Override
        public int getItemCount() {
            return mData.size() == 0 ? 0 : mData.size() + 1;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return ITEM_HEADER;
            }
            return ITEM_CONTENT;
        }

        @Override
        public boolean checkViewType(int viewType) {
            return viewType == ITEM_HEADER || viewType == ITEM_CONTENT;
        }

        class ContentViewHolder extends RecyclerView.ViewHolder {
            TextView nameView;
            TextView phoneView;
            CheckBox checkBox;

            public ContentViewHolder(View itemView) {
                super(itemView);
                nameView = (TextView) itemView.findViewById(R.id.name);
                phoneView = (TextView) itemView.findViewById(R.id.phone);
                checkBox = (CheckBox) itemView.findViewById(R.id.checkbox);
            }
        }

        class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView titleView;
            CheckBox checkBox;

            public HeaderViewHolder(View itemView) {
                super(itemView);
                titleView = (TextView) itemView.findViewById(R.id.title);
                checkBox = (CheckBox) itemView.findViewById(R.id.checkbox);
            }
        }
    }

    private class HandleContactTask extends AsyncTask<Void, Void, String> {

        private List<Contact> mDupContacts = new ArrayList<>();
        private List<Contact> mContacts = new ArrayList<>();
        @Override
        protected String doInBackground(Void... params) {
            Map<String, String> map = divideContacts(getContentResolver());
            generateContracts(getContentResolver(), map, mDupContacts, mContacts);
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            hideProgressDialog();
            mDupContactAdapter.setData(mDupContacts);
            mContactAdapter.setData(mContacts);
            mergeRecyclerAdapter.notifyDataSetChanged();
        }

        @Override
        protected void onPreExecute() {
            showProgressDialog();
        }

        @Override
        protected void onProgressUpdate(Void... values) {
        }
    }
}
