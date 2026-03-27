<?xml version="1.0" encoding="utf-8"?>
<?php
header('Content-Type: application/json');
require_once 'db_config.php';

$tenant_id = isset($_GET['tenant_id']) ? intval($_GET['tenant_id']) : 1;
$user_id   = isset($_GET['user_id']) ? intval($_GET['user_id']) : 0;
$action    = isset($_GET['action']) ? $_GET['action'] : 'list';

if ($action == 'list' && $user_id > 0) {
    // Mocking conversation list logic - In real world, join with messages table
    // Fetch distinct practitioners who have messaged this patient or vice versa
    $data = [
        [
            "id" => 1,
            "name" => "Dr. Reyes, OB-GYN",
            "last_message" => "Your next checkup is on Monday 10AM...",
            "time" => "10:42 AM",
            "unread" => 2,
            "status" => "online",
            "avatar" => "dr_reyes"
        ],
        [
            "id" => 2,
            "name" => "Midwife Anna",
            "last_message" => "Opo Mommy, as long as may prescription...",
            "time" => "Yesterday",
            "unread" => 0,
            "status" => "offline",
            "avatar" => "midwife_anna"
        ],
        [
            "id" => 3,
            "name" => "Admin Hub",
            "last_message" => "Payment for March has been verified.",
            "time" => "2 days ago",
            "unread" => 0,
            "status" => "online",
            "avatar" => "admin"
        ]
    ];

    echo json_encode([
        "success" => true,
        "tenant_id" => $tenant_id,
        "data" => $data
    ]);
} else {
    echo json_encode(["success" => false, "message" => "Invalid parameters"]);
}
?>
