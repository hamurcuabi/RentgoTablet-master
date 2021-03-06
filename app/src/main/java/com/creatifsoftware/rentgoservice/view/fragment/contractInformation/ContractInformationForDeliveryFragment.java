package com.creatifsoftware.rentgoservice.view.fragment.contractInformation;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;

import com.creatifsoftware.rentgoservice.BuildConfig;
import com.creatifsoftware.rentgoservice.R;
import com.creatifsoftware.rentgoservice.databinding.FragmentContractInformationForDeliveryBinding;
import com.creatifsoftware.rentgoservice.di.Injectable;
import com.creatifsoftware.rentgoservice.model.ContractItem;
import com.creatifsoftware.rentgoservice.model.User;
import com.creatifsoftware.rentgoservice.model.base.ResponseResult;
import com.creatifsoftware.rentgoservice.model.request.GetContractInformationRequest;
import com.creatifsoftware.rentgoservice.utils.BlobStorageManager;
import com.creatifsoftware.rentgoservice.utils.EnumUtils;
import com.creatifsoftware.rentgoservice.utils.ImageUtil;
import com.creatifsoftware.rentgoservice.view.callback.DrivingLicenseImageClickCallback;
import com.creatifsoftware.rentgoservice.view.fragment.EquipmentListFragment;
import com.creatifsoftware.rentgoservice.view.fragment.base.BaseFragment;
import com.creatifsoftware.rentgoservice.viewmodel.ContractInformationViewModel;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

import javax.inject.Inject;

import id.zelory.compressor.Compressor;

import static android.app.Activity.RESULT_OK;

/**
 * Created by kerembalaban on 16.02.2019 at 18:38.
 */
public class ContractInformationForDeliveryFragment extends BaseFragment implements Injectable {
    private static final int REQUEST_FRONT_LICENSE_CAPTURE_IMAGE = 100;
    private static final int REQUEST_REAR_LICENSE_CAPTURE_IMAGE = 101;
    private static final int MY_CAMERA_PERMISSION_CODE = 102;
    private final DrivingLicenseImageClickCallback drivingLicenseImageClickCallback = this::openCameraIntent;
    public FragmentContractInformationForDeliveryBinding binding;
    public ContractInformationViewModel viewModel;
    @Inject
    ViewModelProvider.Factory viewModelFactory;
    private boolean hasServiceError = false;
    private boolean hasBlobStorageError = false;
    private int mYear;
    private int mMonth;
    private int mDay;
    private int mHour;
    private int mMinute;

    public static ContractInformationForDeliveryFragment forSelectedContract(ContractItem selectedContract) {
        ContractInformationForDeliveryFragment fragment = new ContractInformationForDeliveryFragment();
        Bundle args = new Bundle();

        args.putSerializable(KEY_SELECTED_CONTRACT, selectedContract);
        fragment.setArguments(args);
        return fragment;
    }

    @androidx.annotation.Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @androidx.annotation.Nullable ViewGroup container,
                             @androidx.annotation.Nullable Bundle savedInstanceState) {

        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_contract_information_for_delivery, container, false);
        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getStepView().go(0, true);

        mActivity.showProgressBar();
        viewModel = ViewModelProviders.of(mActivity, viewModelFactory)
                .get(ContractInformationViewModel.class);
        selectedContract = getSelectedContract();
        binding.contractInformationTitle.setText(String.format(Locale.getDefault(), "%s - %s (%s)", getString(R.string.contract_information), selectedContract.contractNumber, selectedContract.pnrNumber));
        binding.pickupDateTime.setOnClickListener(view -> datePicker());
        bindViewModal();
        //observeContractInformation(selectedContract.contractId);
    }

    private void datePicker() {
        final Calendar c = Calendar.getInstance();
        mYear = c.get(Calendar.YEAR);
        mMonth = c.get(Calendar.MONTH);
        mDay = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(mActivity,
                (view, year, monthOfYear, dayOfMonth) -> {

                    mYear = year;
                    mMonth = monthOfYear;
                    mDay = dayOfMonth;
                    timePicker();
                }, mYear, mMonth, mDay);

        String userId = new User().getUser(getContext()).userId;
        if (BuildConfig.IS_PROD && !userId.equals("cf4b9a11-75b3-e911-a851-000d3a2dd1b4")) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, -1);
            datePickerDialog.getDatePicker().setMinDate(cal.getTimeInMillis());
        }

        datePickerDialog.show();
    }

    private void timePicker() {
        final Calendar c = Calendar.getInstance();
        mHour = c.get(Calendar.HOUR_OF_DAY);
        mMinute = c.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(mActivity,
                (view, hourOfDay, minute) -> {
                    mHour = hourOfDay;
                    mMinute = minute;

                    selectedContract.isManuelProcess = true;
                    Calendar calendar = Calendar.getInstance();
                    calendar.set(Calendar.YEAR, mYear);
                    calendar.set(Calendar.MONTH, mMonth);
                    calendar.set(Calendar.DAY_OF_MONTH, mDay);
                    calendar.set(Calendar.HOUR_OF_DAY, mHour);// for 6 hour
                    calendar.set(Calendar.MINUTE, mMinute);// for 0 min
                    calendar.set(Calendar.SECOND, 0);// for 0 sec
                    selectedContract.manuelPickupDateTimeStamp = (calendar.getTimeInMillis() / 1000);
                    selectedContract.pickupTimestamp = (calendar.getTimeInMillis() / 1000);
                    viewModel.setContractInformation(selectedContract);
                    binding.setContract(selectedContract);

                }, mHour, mMinute, true);
        timePickerDialog.show();
    }

    @Override
    public void backButtonClicked() {
        selectedContract = null;
        viewModel.getContractInformationObservable().removeObservers(getViewLifecycleOwner());
        super.backButtonClicked();
        //mActivity.hideProgressBar();
    }

    private void observeContractInformation(String contractId) {
        if (viewModel.getContractInformationObservable().hasObservers()) {
            selectedContract = viewModel.getContractInformationObservable().getValue();
            bindViewModal();
            return;
        }

        super.showLoading();
        GetContractInformationRequest request = new GetContractInformationRequest();
        request.setContractId(contractId);
        request.setStatusCode(EnumUtils.ContractStatusCode.WAITING_FOR_DELIVERY.getIntValue());

        viewModel.setGetContractInformationRequest(request);
        viewModel.getContractInformationObservable().observe(this, contractItem -> {
            if (contractItem == null) {
                super.hideLoading();
                hasServiceError = true;
                showMessageDialog(getString(R.string.unknown_error_message));
            } else if (!contractItem.responseResult.result) {
                super.hideLoading();
                hasServiceError = true;
                showMessageDialog(contractItem.responseResult.exceptionDetail);
            } else if (contractItem.contractId.equals(selectedContract.contractId)) {
                super.hideLoading();
                selectedContract = contractItem;
                selectedContract.depositAmount = selectedContract.groupCodeInformation.depositAmount;
                bindViewModal();
            }
        });
    }

    @Override
    public void messageDialogDoneButtonClicked() {
        if (hasServiceError) {
            viewModel.getContractInformationObservable().removeObservers(this);
            mActivity.onBackPressed();
            //mActivity.getSupportFragmentManager().popBackStackImmediate();
        }
    }

    private void bindViewModal() {
        viewModel.setContractInformation(selectedContract);
        binding.setContract(selectedContract);
        binding.setCallback(drivingLicenseImageClickCallback);

//        if (selectedContract.customer.drivingLicenseFrontImage != null){
//            binding.licenseFrontFacePhoto.setImageBitmap(ImageUtil.instance.convertFiletoBitmap(selectedContract.customer.drivingLicenseFrontImage));
//            binding.licenseFrontFacePhotoCheckbox.setChecked(true);
//        }
//        if (selectedContract.customer.drivingLicenseRearImage != null){
//            binding.licenseBackFacePhoto.setImageBitmap(ImageUtil.instance.convertFiletoBitmap(selectedContract.customer.drivingLicenseRearImage));
//            binding.licenseBackFacePhotoCheckbox.setChecked(true);
//        }

        if (selectedContract.isEquipmentChanged) {
            super.showLoading();
            downloadPhoto("license_front_image", true);
            downloadPhoto("license_rear_image", false);
        } else {
            if (selectedContract.customer.drivingLicenseFrontImage != null) {
                binding.licenseFrontFacePhoto.setImageBitmap(ImageUtil.instance.convertFiletoBitmap(selectedContract.customer.drivingLicenseFrontImage));
                binding.licenseFrontFacePhotoCheckbox.setChecked(true);
            }
            if (selectedContract.customer.drivingLicenseRearImage != null) {
                binding.licenseBackFacePhoto.setImageBitmap(ImageUtil.instance.convertFiletoBitmap(selectedContract.customer.drivingLicenseRearImage));
                binding.licenseBackFacePhotoCheckbox.setChecked(true);
            }
        }
    }

    private void downloadPhoto(String imageName, boolean isFront) {
        String refName = BlobStorageManager.instance.prepareCustomerDrivingLicenseImageName(selectedContract.customer.customerId, imageName);
        final ByteArrayOutputStream imageStream = new ByteArrayOutputStream();
        final Handler handler = new Handler();
        Thread th = new Thread(() -> {
            try {
                long imageLength = 0;
                BlobStorageManager.instance.GetImage(BlobStorageManager.instance.getCustomerContainerName(), refName, imageStream, imageLength);
                handler.post(() -> {
                    byte[] buffer = imageStream.toByteArray();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(buffer, 0, buffer.length);
                    if (isFront) {
                        binding.licenseFrontFacePhoto.setImageBitmap(bitmap);
                        binding.licenseFrontFacePhotoCheckbox.setChecked(true);
                    } else {
                        super.hideLoading();
                        binding.licenseBackFacePhoto.setImageBitmap(bitmap);
                        binding.licenseBackFacePhotoCheckbox.setChecked(true);
                    }
                    imageStream.reset();
                });
            } catch (Exception ex) {
                final String exceptionMessage = ex.getMessage();
                handler.post(imageStream::reset);
            }
        });
        th.start();
    }

    @Override
    protected void navigate() {
        super.navigate();
        if (selectedContract.isEquipmentChanged) {
            showEquipmentListFragment();
        } else {
            uploadDrivingLicensePhotos();
        }

//        selectedContract.additionalProductList = new ArrayList<>();
//        selectedContract.addedAdditionalProducts = new ArrayList<>();
//        selectedContract.addedAdditionalServices = new ArrayList<>();
//        selectedContract.updatedGroupCodeInformation = null;
//        EquipmentListFragment equipmentListFragment = EquipmentListFragment.forSelectedContract(selectedContract);
//        super.changeFragment(equipmentListFragment);
    }

    private void uploadDrivingLicensePhotos() {
        hasBlobStorageError = false;
        super.showLoading();
        (new Handler()).postDelayed(this::upload, 1000);
    }

    private void upload() {
        Thread thread = new Thread(() -> {
            try {
                String imageName = BlobStorageManager.instance.prepareCustomerDrivingLicenseImageName(
                        selectedContract.customer.customerId,
                        "license_front_image");

                BlobStorageManager.instance.UploadImage(BlobStorageManager.instance.getCustomerContainerName(), selectedContract.customer.drivingLicenseFrontImage, imageName);

                String imageName2 = BlobStorageManager.instance.prepareCustomerDrivingLicenseImageName(
                        selectedContract.customer.customerId,
                        "license_rear_image");

                BlobStorageManager.instance.UploadImage(BlobStorageManager.instance.getCustomerContainerName(), selectedContract.customer.drivingLicenseRearImage, imageName2);

            } catch (Exception e) {
                hasBlobStorageError = true;
                showMessageDialog(String.format(Locale.getDefault(), "Fotoğraf atma hatası\n\n%s", e.getLocalizedMessage()));
                e.printStackTrace();
            }
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        super.hideLoading();
        if (!hasBlobStorageError)
            showEquipmentListFragment();
    }

    private void showEquipmentListFragment() {
        selectedContract.additionalProductList = new ArrayList<>();
        selectedContract.addedAdditionalProducts = new ArrayList<>();
        selectedContract.addedAdditionalServices = new ArrayList<>();
        selectedContract.updatedGroupCodeInformation = null;
        EquipmentListFragment equipmentListFragment = EquipmentListFragment.forSelectedContract(selectedContract);
        super.changeFragment(equipmentListFragment);
    }

    @Override
    protected ResponseResult checkBeforeNavigate() {
        if (!binding.licenseFrontFacePhotoCheckbox.isChecked()) {
            return new ResponseResult(false, getString(R.string.license_front_photo_empty_error));
        } else if (!binding.licenseBackFacePhotoCheckbox.isChecked()) {
            return new ResponseResult(false, getString(R.string.license_back_photo_empty_error));
        }

        return super.checkBeforeNavigate();
    }

    @Override
    public void permissionGranted() {
        super.permissionGranted();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_PERMISSION_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCameraIntent(getView());
            } else {
                Toast.makeText(getContext(), "camera permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openCameraIntent(View view) {
        if (mActivity.checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA},
                    MY_CAMERA_PERMISSION_CODE);
        } else {
            //Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            //Intent cameraIntent = new Intent("android.media.action.IMAGE_CAPTURE");

            Intent intent = ImageUtil.instance.dispatchTakePictureIntent(mActivity);
            startActivityForResult(intent, view.getId() == R.id.license_front_image_layout ?
                    REQUEST_FRONT_LICENSE_CAPTURE_IMAGE :
                    REQUEST_REAR_LICENSE_CAPTURE_IMAGE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            File compressedImage = ImageUtil.instance.getImageFile();
            try {
                compressedImage = new Compressor(mActivity.getApplicationContext()).setQuality(50).compressToFile(ImageUtil.instance.getImageFile());
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (requestCode == REQUEST_FRONT_LICENSE_CAPTURE_IMAGE) {
                selectedContract.customer.drivingLicenseFrontImage = compressedImage;
                binding.licenseFrontFacePhoto.setImageBitmap(ImageUtil.instance.convertFiletoBitmap(selectedContract.customer.drivingLicenseFrontImage));
                binding.licenseFrontFacePhotoCheckbox.setChecked(true);
                ImageUtil.instance.removeImageFile();
            } else {
                selectedContract.customer.drivingLicenseRearImage = compressedImage;
                binding.licenseBackFacePhoto.setImageBitmap(ImageUtil.instance.convertFiletoBitmap(selectedContract.customer.drivingLicenseRearImage));
                binding.licenseBackFacePhotoCheckbox.setChecked(true);
                ImageUtil.instance.removeImageFile();
            }
        }
    }
}
