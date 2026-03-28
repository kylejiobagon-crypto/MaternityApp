package com.example.alagwaapp;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;

public interface ApiService {
    @GET("api_billing.php")
    Call<SummaryResponse> getSummary(
            @Query("action") String action,
            @Query("mobile") String mobile,
            @Query("email") String email
    );

    @GET("api_login.php")
    Call<ClinicResponse> getClinicInfo(
            @Query("action") String action,
            @Query("tenant_id") int tenantId,
            @Query("mobile") String mobile
    );

    @GET("api_billing.php")
    Call<ResponseBody> getSummaryRaw(
            @Query("action") String action,
            @Query("mobile") String mobile
    );

    @GET("api_patients.php")
    Call<PatientListResponse> getPatients(
            @Query("action") String action,
            @Query("mobile") String mobile,
            @Query("email") String email
    );

    @GET("api_patients.php")
    Call<ResponseBody> getPatientsRaw(@Query("action") String action, @Query("mobile") String mobile);

    @GET("api_chats.php")
    Call<ChatResponse> fetchChats(
            @Query("action") String action,
            @Query("tenant_id") int tenantId,
            @Query("user_id") int userId
    );

    @GET("api_bookings.php")
    Call<AppointmentResponse> getAppointments(
            @Query("action") String action,
            @Query("mobile") String mobile,
            @Query("email") String email
    );

    @POST("api_bookings.php")
    @FormUrlEncoded
    Call<ResponseBody> createAppointment(
            @Query("action") String action,
            @Query("mobile") String mobile,
            @Field("patient_id") int patientId,
            @Field("booking_date") String date,
            @Field("booking_time") String time,
            @Field("service_type") String serviceType,
            @Field("notes") String notes,
            @Field("philhealth_number") String philhealthNumber
    );

    @GET("api_bookings.php")
    Call<ResponseBody> getAvailableSlots(
            @Query("action") String action,
            @Query("mobile") String mobile,
            @Query("date") String date
    );

    @GET("api_bookings.php")
    Call<ResponseBody> getBookingsRaw(@Query("action") String action, @Query("mobile") String mobile);

    @GET("api_dashboard.php")
    Call<ResponseBody> getDashboardRaw(@Query("action") String action, @Query("mobile") String mobile);

    @GET("api_checkups.php")
    Call<ResponseBody> getCheckupsRaw(@Query("action") String action, @Query("mobile") String mobile);

    @GET("api_patient_flow.php")
    Call<ResponseBody> getPatientFlowRaw(@Query("action") String action, @Query("mobile") String mobile);

    @POST("api_login.php")
    @FormUrlEncoded
    Call<ResponseBody> login(
            @Field("username") String username,
            @Field("password") String password,
            @Field("tenant_id") int tenantId,
            @Field("mobile") String mobile
    );

    @POST("api_patients.php")
    @FormUrlEncoded
    Call<ProfileUpdateResponse> updateProfile(
            @Query("action") String action,
            @Query("mobile") String mobile,
            @Field("patient_id") int patientId,
            @Field("first_name") String firstName,
            @Field("last_name") String lastName,
            @Field("email") String email,
            @Field("contact_number") String contactNumber,
            @Field("dob") String dob,
            @Field("address") String address,
            @Field("philhealth_number") String philhealthNumber,
            @Field("lmp") String lmp,
            @Field("blood_type") String bloodType,
            @Field("months_pregnant") Double monthsPregnant,
            @Field("emergency_name") String emergencyName,
            @Field("emergency_relationship") String emergencyRelationship,
            @Field("emergency_number") String emergencyNumber
    );

    @POST("api_downpayment.php")
    @FormUrlEncoded
    Call<ResponseBody> submitDownpaymentProof(
            @Query("action") String action,
            @Query("mobile") String mobile,
            @Field("payment_id") int paymentId,
            @Field("reference_number") String referenceNumber
    );
}
