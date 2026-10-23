-- V11__seed_data.sql
-- Seed comprehensive realistic demonstration data across 4 properties in Vietnam:
-- LUMORA Đà Lạt, Sài Gòn, Vũng Tàu, Hà Nội.
-- Includes RoomTypes, Physical Rooms (with 1 blocked room for maintenance demo),
-- Amenities, Room Images, Users (ADMIN, MANAGERs, USERs), Property Permissions,
-- Diverse Bookings (COMPLETED, CHECKED_IN, CONFIRMED, CANCELLED, PENDING_PAYMENT),
-- Payments, Refunds, Room Access Codes, and Audit Logs.

-- ============================================================================
-- 1. PROPERTIES
-- ============================================================================
INSERT INTO properties (id, name, slug, description, address, city, latitude, longitude, check_in_time, check_out_time, status)
VALUES
  (
    '11111111-1111-1111-1111-111111111111',
    'LUMORA Đà Lạt',
    'lumora-da-lat',
    'Một chốn dừng chân ẩn mình giữa rừng thông lộng gió tại Đà Lạt. Không gian tĩnh lặng, ánh sáng tự nhiên và gỗ mộc mạc mang đến sự an yên trọn vẹn.',
    '12 Khởi Nghĩa Bắc Sơn, Phường 10, TP. Đà Lạt',
    'Đà Lạt',
    11.940419,
    108.458313,
    '14:00',
    '11:00',
    'ACTIVE'
  ),
  (
    '22222222-2222-2222-2222-222222222222',
    'LUMORA Sài Gòn',
    'lumora-sai-gon',
    'Khoảng xanh bình yên giữa lòng phố thị náo nhiệt. Ngôi nhà sân vườn với phong cách tối giản, tinh tế cho những khoảnh khắc nghỉ ngơi thư thái.',
    '45/2 Nguyễn Thị Minh Khai, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh',
    'TP. Hồ Chí Minh',
    10.782622,
    106.698177,
    '14:00',
    '11:00',
    'ACTIVE'
  ),
  (
    '33333333-3333-3333-3333-333333333333',
    'LUMORA Vũng Tàu',
    'lumora-vung-tau',
    'Biệt thự hướng biển Bãi Dâu với ban công đón gió lộng, ngắm trọn hoàng hôn trên biển và âm thanh sóng vỗ rì rào.',
    '84/6 Trần Phú, Phường 5, TP. Vũng Tàu',
    'Vũng Tàu',
    10.362145,
    107.067341,
    '14:00',
    '12:00',
    'ACTIVE'
  ),
  (
    '44444444-4444-4444-4444-444444444444',
    'LUMORA Hà Nội',
    'lumora-ha-noi',
    'Không gian mang đậm dấu ấn kiến trúc Đông Dương hoài niệm giữa lòng phố cổ Hà Nội, chỉ vài bước chân ra Hồ Gươm.',
    '28 Hàng Bè, Phường Hàng Bạc, Quận Hoàn Kiếm, Hà Nội',
    'Hà Nội',
    21.033285,
    105.854128,
    '14:00',
    '12:00',
    'ACTIVE'
  )
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 2. AMENITIES
-- ============================================================================
INSERT INTO amenities (id, name, icon)
VALUES
  ('a0000001-0000-0000-0000-000000000001', 'High-speed Wi-Fi', 'wifi'),
  ('a0000002-0000-0000-0000-000000000002', 'Espresso machine', 'coffee'),
  ('a0000003-0000-0000-0000-000000000003', 'Soaking bathtub', 'bath'),
  ('a0000004-0000-0000-0000-000000000004', 'Marshall Bluetooth speaker', 'speaker'),
  ('a0000005-0000-0000-0000-000000000005', 'Dedicated workspace', 'briefcase'),
  ('a0000006-0000-0000-0000-000000000006', 'Climate control / AC', 'wind'),
  ('a0000007-0000-0000-0000-000000000007', 'Private balcony', 'sun'),
  ('a0000008-0000-0000-0000-000000000008', 'Organic cotton linens', 'sparkles'),
  ('a0000009-0000-0000-0000-000000000009', 'Infinity pool access', 'waves'),
  ('a0000010-0000-0000-0000-000000000010', 'Ocean sunset view', 'sunset')
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 3. ROOM TYPES
-- ============================================================================
INSERT INTO room_types (id, property_id, name, description, base_price, max_guests, bed_type, bed_count, size_m2, status)
VALUES
  -- Đà Lạt Room Types
  (
    'b1111111-0001-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    'Deluxe Room',
    'Phòng tiêu chuẩn với cửa sổ kính lớn hướng đồi thông, sàn gỗ ấm cúng và bồn tắm thư giãn.',
    299000.00,
    2,
    'King bed',
    1,
    32.00,
    'ACTIVE'
  ),
  (
    'b1111111-0002-0000-0000-000000000002',
    '11111111-1111-1111-1111-111111111111',
    'Luxury Suite',
    'Không gian rộng rãi tầng cao với ban công riêng ngắm hoàng hôn Đà Lạt, loa Marshall và máy pha cà phê cao cấp.',
    399000.00,
    2,
    'King bed',
    1,
    45.00,
    'ACTIVE'
  ),
  (
    'b1111111-0003-0000-0000-000000000003',
    '11111111-1111-1111-1111-111111111111',
    'Forest Villa Suite',
    'Căn villa độc lập nép mình dưới rặng thông cổ thụ, nội thất gỗ óc chó và lò sưởi ấm áp.',
    499000.00,
    4,
    'King bed + Twin bed',
    2,
    65.00,
    'ACTIVE'
  ),
  -- Sài Gòn Room Types
  (
    'b2222222-0001-0000-0000-000000000001',
    '22222222-2222-2222-2222-222222222222',
    'Minimal Studio',
    'Căn studio yên tĩnh giữa lòng Sài Gòn, tối ưu cho những giờ làm việc tập trung hoặc kỳ nghỉ ngắn.',
    249000.00,
    2,
    'Queen bed',
    1,
    28.00,
    'ACTIVE'
  ),
  (
    'b2222222-0002-0000-0000-000000000002',
    '22222222-2222-2222-2222-222222222222',
    'Courtyard Loft',
    'Thiết kế thông tầng thoáng đãng nhìn ra khu vườn nhiệt đới xanh mát, ánh sáng tự nhiên ngập tràn.',
    349000.00,
    2,
    'King bed',
    1,
    42.00,
    'ACTIVE'
  ),
  -- Vũng Tàu Room Types
  (
    'b3333333-0001-0000-0000-000000000001',
    '33333333-3333-3333-3333-333333333333',
    'Ocean View Studio',
    'Phòng studio đón trọn ánh bình minh trên biển Bãi Dâu, ban công kính nhìn thẳng ra biển.',
    329000.00,
    2,
    'King bed',
    1,
    35.00,
    'ACTIVE'
  ),
  (
    'b3333333-0002-0000-0000-000000000002',
    '33333333-3333-3333-3333-333333333333',
    'Sunset Beachfront Suite',
    'Suite cao cấp với bồn tắm view biển, ban công rộng rãi ngắm trọn vẹn hoàng hôn chiều tà.',
    459000.00,
    3,
    'King bed + Sofa bed',
    2,
    55.00,
    'ACTIVE'
  ),
  -- Hà Nội Room Types
  (
    'b4444444-0001-0000-0000-000000000001',
    '44444444-4444-4444-4444-444444444444',
    'Old Quarter Heritage Suite',
    'Kiến trúc mái vòm cổ kính kết hợp nội thất mây tre đan tinh xảo ngay giữa lòng phố cổ Hoàn Kiếm.',
    359000.00,
    2,
    'King bed',
    1,
    38.00,
    'ACTIVE'
  ),
  (
    'b4444444-0002-0000-0000-000000000002',
    '44444444-4444-4444-4444-444444444444',
    'French Colonial Balcony Room',
    'Phòng mang phong cách Pháp cổ điển với ban công hoa sắt nhìn xuống dãy phố rợp bóng cây xanh.',
    389000.00,
    2,
    'Queen bed',
    1,
    40.00,
    'ACTIVE'
  )
ON CONFLICT (id, property_id) DO NOTHING;

-- Room Type Amenities
INSERT INTO room_type_amenities (room_type_id, amenity_id)
VALUES
  -- Đà Lạt Deluxe
  ('b1111111-0001-0000-0000-000000000001', 'a0000001-0000-0000-0000-000000000001'),
  ('b1111111-0001-0000-0000-000000000001', 'a0000003-0000-0000-0000-000000000003'),
  ('b1111111-0001-0000-0000-000000000001', 'a0000006-0000-0000-0000-000000000006'),
  ('b1111111-0001-0000-0000-000000000001', 'a0000008-0000-0000-0000-000000000008'),
  -- Đà Lạt Luxury
  ('b1111111-0002-0000-0000-000000000002', 'a0000001-0000-0000-0000-000000000001'),
  ('b1111111-0002-0000-0000-000000000002', 'a0000002-0000-0000-0000-000000000002'),
  ('b1111111-0002-0000-0000-000000000002', 'a0000003-0000-0000-0000-000000000003'),
  ('b1111111-0002-0000-0000-000000000002', 'a0000004-0000-0000-0000-000000000004'),
  ('b1111111-0002-0000-0000-000000000002', 'a0000007-0000-0000-0000-000000000007'),
  -- Đà Lạt Villa
  ('b1111111-0003-0000-0000-000000000003', 'a0000001-0000-0000-0000-000000000001'),
  ('b1111111-0003-0000-0000-000000000003', 'a0000002-0000-0000-0000-000000000002'),
  ('b1111111-0003-0000-0000-000000000003', 'a0000003-0000-0000-0000-000000000003'),
  ('b1111111-0003-0000-0000-000000000003', 'a0000004-0000-0000-0000-000000000004'),
  ('b1111111-0003-0000-0000-000000000003', 'a0000005-0000-0000-0000-000000000005'),
  ('b1111111-0003-0000-0000-000000000003', 'a0000007-0000-0000-0000-000000000007'),
  -- Sài Gòn Studio & Loft
  ('b2222222-0001-0000-0000-000000000001', 'a0000001-0000-0000-0000-000000000001'),
  ('b2222222-0001-0000-0000-000000000001', 'a0000005-0000-0000-0000-000000000005'),
  ('b2222222-0001-0000-0000-000000000001', 'a0000006-0000-0000-0000-000000000006'),
  ('b2222222-0002-0000-0000-000000000002', 'a0000001-0000-0000-0000-000000000001'),
  ('b2222222-0002-0000-0000-000000000002', 'a0000002-0000-0000-0000-000000000002'),
  ('b2222222-0002-0000-0000-000000000002', 'a0000004-0000-0000-0000-000000000004'),
  -- Vũng Tàu
  ('b3333333-0001-0000-0000-000000000001', 'a0000001-0000-0000-0000-000000000001'),
  ('b3333333-0001-0000-0000-000000000001', 'a0000007-0000-0000-0000-000000000007'),
  ('b3333333-0001-0000-0000-000000000001', 'a0000010-0000-0000-0000-000000000010'),
  ('b3333333-0002-0000-0000-000000000002', 'a0000003-0000-0000-0000-000000000003'),
  ('b3333333-0002-0000-0000-000000000002', 'a0000009-0000-0000-0000-000000000009'),
  ('b3333333-0002-0000-0000-000000000002', 'a0000010-0000-0000-0000-000000000010'),
  -- Hà Nội
  ('b4444444-0001-0000-0000-000000000001', 'a0000001-0000-0000-0000-000000000001'),
  ('b4444444-0001-0000-0000-000000000001', 'a0000002-0000-0000-0000-000000000002'),
  ('b4444444-0001-0000-0000-000000000001', 'a0000005-0000-0000-0000-000000000005'),
  ('b4444444-0002-0000-0000-000000000002', 'a0000001-0000-0000-0000-000000000001'),
  ('b4444444-0002-0000-0000-000000000002', 'a0000007-0000-0000-0000-000000000007')
ON CONFLICT (room_type_id, amenity_id) DO NOTHING;

-- ============================================================================
-- 4. PHYSICAL ROOMS (12 physical rooms, 1 blocked by Admin)
-- ============================================================================
INSERT INTO rooms (id, property_id, room_type_id, room_number, name, floor, status, is_blocked)
VALUES
  -- Đà Lạt Rooms
  (
    'c1111111-0001-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    'b1111111-0001-0000-0000-000000000001',
    'No5',
    'No5 Deluxe Room',
    '1',
    'ACTIVE',
    false
  ),
  (
    'c1111111-0002-0000-0000-000000000002',
    '11111111-1111-1111-1111-111111111111',
    'b1111111-0001-0000-0000-000000000001',
    'No6',
    'No6 Deluxe Room',
    '2',
    'ACTIVE',
    false
  ),
  (
    'c1111111-0003-0000-0000-000000000003',
    '11111111-1111-1111-1111-111111111111',
    'b1111111-0002-0000-0000-000000000002',
    'No7',
    'No7 Luxury Room',
    '2',
    'ACTIVE',
    false
  ),
  (
    'c1111111-0004-0000-0000-000000000004',
    '11111111-1111-1111-1111-111111111111',
    'b1111111-0003-0000-0000-000000000003',
    'No8',
    'No8 Forest Villa',
    '1',
    'ACTIVE',
    true -- Admin Blocked for maintenance demo
  ),
  -- Sài Gòn Rooms
  (
    'c2222222-0001-0000-0000-000000000001',
    '22222222-2222-2222-2222-222222222222',
    'b2222222-0001-0000-0000-000000000001',
    'No1',
    'No1 Minimal Studio',
    '1',
    'ACTIVE',
    false
  ),
  (
    'c2222222-0002-0000-0000-000000000002',
    '22222222-2222-2222-2222-222222222222',
    'b2222222-0002-0000-0000-000000000002',
    'No2',
    'No2 Courtyard Loft',
    '2',
    'ACTIVE',
    false
  ),
  (
    'c2222222-0003-0000-0000-000000000003',
    '22222222-2222-2222-2222-222222222222',
    'b2222222-0001-0000-0000-000000000001',
    'No3',
    'No3 Garden Studio',
    '1',
    'ACTIVE',
    false
  ),
  -- Vũng Tàu Rooms
  (
    'c3333333-0001-0000-0000-000000000001',
    '33333333-3333-3333-3333-333333333333',
    'b3333333-0001-0000-0000-000000000001',
    'No101',
    'No101 Ocean Breeze',
    '1',
    'ACTIVE',
    false
  ),
  (
    'c3333333-0002-0000-0000-000000000002',
    '33333333-3333-3333-3333-333333333333',
    'b3333333-0002-0000-0000-000000000002',
    'No201',
    'No201 Sunset Panorama',
    '2',
    'ACTIVE',
    false
  ),
  -- Hà Nội Rooms
  (
    'c4444444-0001-0000-0000-000000000001',
    '44444444-4444-4444-4444-444444444444',
    'b4444444-0001-0000-0000-000000000001',
    'No301',
    'No301 Heritage Room',
    '3',
    'ACTIVE',
    false
  ),
  (
    'c4444444-0002-0000-0000-000000000002',
    '44444444-4444-4444-4444-444444444444',
    'b4444444-0001-0000-0000-000000000001',
    'No302',
    'No302 Indochine Suite',
    '3',
    'ACTIVE',
    false
  ),
  (
    'c4444444-0003-0000-0000-000000000003',
    '44444444-4444-4444-4444-444444444444',
    'b4444444-0002-0000-0000-000000000002',
    'No401',
    'No401 French Balcony',
    '4',
    'ACTIVE',
    false
  )
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 5. ROOM & ROOM TYPE IMAGES
-- ============================================================================
INSERT INTO room_images (id, room_id, room_type_id, image_url, sort_order, is_cover)
VALUES
  -- Room Type Cover Images
  ('d1111111-0001-0000-0000-000000000001', NULL, 'b1111111-0001-0000-0000-000000000001', 'https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=1200&q=80', 0, true),
  ('d1111111-0002-0000-0000-000000000002', NULL, 'b1111111-0002-0000-0000-000000000002', 'https://images.unsplash.com/photo-1566665797739-1674de7a421a?auto=format&fit=crop&w=1200&q=80', 0, true),
  ('d1111111-0003-0000-0000-000000000003', NULL, 'b1111111-0003-0000-0000-000000000003', 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80', 0, true),
  ('d2222222-0001-0000-0000-000000000001', NULL, 'b2222222-0001-0000-0000-000000000001', 'https://images.unsplash.com/photo-1598928506311-c55ded91a20c?auto=format&fit=crop&w=1200&q=80', 0, true),
  ('d2222222-0002-0000-0000-000000000002', NULL, 'b2222222-0002-0000-0000-000000000002', 'https://images.unsplash.com/photo-1522771739844-6a9f6d5f14af?auto=format&fit=crop&w=1200&q=80', 0, true),
  ('d3333333-0001-0000-0000-000000000001', NULL, 'b3333333-0001-0000-0000-000000000001', 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=1200&q=80', 0, true),
  ('d3333333-0002-0000-0000-000000000002', NULL, 'b3333333-0002-0000-0000-000000000002', 'https://images.unsplash.com/photo-1507652313519-d4e9174996dd?auto=format&fit=crop&w=1200&q=80', 0, true),
  ('d4444444-0001-0000-0000-000000000001', NULL, 'b4444444-0001-0000-0000-000000000001', 'https://images.unsplash.com/photo-1618773928121-c32242e63f39?auto=format&fit=crop&w=1200&q=80', 0, true),
  ('d4444444-0002-0000-0000-000000000002', NULL, 'b4444444-0002-0000-0000-000000000002', 'https://images.unsplash.com/photo-1591088398332-8a7791972843?auto=format&fit=crop&w=1200&q=80', 0, true),

  -- Specific Physical Room Images
  ('d5555555-0001-0000-0000-000000000001', 'c1111111-0001-0000-0000-000000000001', NULL, 'https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=1200&q=80', 0, true),
  ('d5555555-0001-0000-0000-000000000002', 'c1111111-0001-0000-0000-000000000001', NULL, 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=1200&q=80', 1, false),
  ('d5555555-0002-0000-0000-000000000001', 'c1111111-0002-0000-0000-000000000002', NULL, 'https://images.unsplash.com/photo-1618773928121-c32242e63f39?auto=format&fit=crop&w=1200&q=80', 0, true),
  ('d5555555-0003-0000-0000-000000000001', 'c1111111-0003-0000-0000-000000000003', NULL, 'https://images.unsplash.com/photo-1566665797739-1674de7a421a?auto=format&fit=crop&w=1200&q=80', 0, true),
  ('d5555555-0004-0000-0000-000000000001', 'c1111111-0004-0000-0000-000000000004', NULL, 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80', 0, true),
  ('d5555555-0005-0000-0000-000000000001', 'c2222222-0001-0000-0000-000000000001', NULL, 'https://images.unsplash.com/photo-1598928506311-c55ded91a20c?auto=format&fit=crop&w=1200&q=80', 0, true),
  ('d5555555-0006-0000-0000-000000000001', 'c2222222-0002-0000-0000-000000000002', NULL, 'https://images.unsplash.com/photo-1522771739844-6a9f6d5f14af?auto=format&fit=crop&w=1200&q=80', 0, true),
  ('d5555555-0007-0000-0000-000000000001', 'c3333333-0001-0000-0000-000000000001', NULL, 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=1200&q=80', 0, true),
  ('d5555555-0008-0000-0000-000000000001', 'c4444444-0001-0000-0000-000000000001', NULL, 'https://images.unsplash.com/photo-1618773928121-c32242e63f39?auto=format&fit=crop&w=1200&q=80', 0, true)
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 6. USERS (ADMIN, MANAGERS, USERS)
-- ============================================================================
INSERT INTO users (id, clerk_user_id, email, first_name, last_name, phone, role, status)
VALUES
  -- 1 Admin
  (
    'e0000001-0000-0000-0000-000000000001',
    'user_2lLumoraOwnerAdmin01',
    'owner@lumora.vn',
    'Lợi',
    'Nguyễn Hồng',
    '0909123456',
    'ADMIN',
    'ACTIVE'
  ),
  -- 3 Managers
  (
    'e0000002-0000-0000-0000-000000000001',
    'user_2lLumoraMgrDaLat01',
    'manager.dalat@lumora.vn',
    'Quân',
    'Trần Minh',
    '0918222333',
    'MANAGER',
    'ACTIVE'
  ),
  (
    'e0000002-0000-0000-0000-000000000002',
    'user_2lLumoraMgrSaiGon01',
    'manager.saigon@lumora.vn',
    'Hà',
    'Lê Thu',
    '0933444555',
    'MANAGER',
    'ACTIVE'
  ),
  (
    'e0000002-0000-0000-0000-000000000003',
    'user_2lLumoraMgrCoast01',
    'manager.coast@lumora.vn',
    'Huy',
    'Phạm Đức',
    '0977888999',
    'MANAGER',
    'ACTIVE'
  ),
  -- 3 Regular Users
  (
    'e0000003-0000-0000-0000-000000000001',
    'usr_mock_123',
    'loitn@lumora.vn',
    'Lợi',
    'Trần',
    '0988776655',
    'USER',
    'ACTIVE'
  ),
  (
    'e0000003-0000-0000-0000-000000000002',
    'user_2lLumoraGuestPhuong',
    'guest.phuong@gmail.com',
    'Phương',
    'Nguyễn Mai',
    '0912345678',
    'USER',
    'ACTIVE'
  ),
  (
    'e0000003-0000-0000-0000-000000000003',
    'user_2lLumoraGuestHoang',
    'guest.hoang@gmail.com',
    'Hoàng',
    'Trần Việt',
    '0934567890',
    'USER',
    'ACTIVE'
  )
ON CONFLICT (id) DO NOTHING;

-- User Properties (Manager Permissions)
INSERT INTO user_properties (id, user_id, property_id)
VALUES
  -- Manager Đà Lạt
  ('f0000001-0000-0000-0000-000000000001', 'e0000002-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111'),
  -- Manager Sài Gòn
  ('f0000002-0000-0000-0000-000000000001', 'e0000002-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222'),
  -- Manager Coast (Vũng Tàu & Hà Nội)
  ('f0000003-0000-0000-0000-000000000001', 'e0000002-0000-0000-0000-000000000003', '33333333-3333-3333-3333-333333333333'),
  ('f0000003-0000-0000-0000-000000000002', 'e0000002-0000-0000-0000-000000000003', '44444444-4444-4444-4444-444444444444')
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 7. BOOKINGS & ASSOCIATED DATA
-- ============================================================================

-- Booking 1: COMPLETED stay at Đà Lạt
INSERT INTO bookings (
  id, user_id, property_id, check_in_date, check_out_date, status,
  booking_code, guest_count, subtotal, discount_amount, total_amount, currency,
  special_request, checked_in_at, checked_out_at, completed_at, check_in_qr_token, created_at
) VALUES (
  '80000001-0000-0000-0000-000000000001',
  'e0000003-0000-0000-0000-000000000001',
  '11111111-1111-1111-1111-111111111111',
  '2026-10-18',
  '2026-10-19',
  'COMPLETED',
  'LMR-2610-8824',
  1,
  299000.00,
  0.00,
  299000.00,
  'VND',
  'Cần phòng yên tĩnh để làm việc',
  '2026-10-18 14:15:00+00',
  '2026-10-19 10:45:00+00',
  '2026-10-19 11:00:00+00',
  'LMR-CHK-8824-DL05',
  '2026-10-15 08:30:00+00'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO booking_rooms (id, booking_id, room_id, property_id, room_type_id, room_number, room_name, price_per_night, number_of_nights, room_total)
VALUES (
  '90000001-0000-0000-0000-000000000001',
  '80000001-0000-0000-0000-000000000001',
  'c1111111-0001-0000-0000-000000000001',
  '11111111-1111-1111-1111-111111111111',
  'b1111111-0001-0000-0000-000000000001',
  'No5',
  'No5 Deluxe Room',
  299000.00,
  1,
  299000.00
) ON CONFLICT (id) DO NOTHING;

INSERT INTO booking_guests (id, booking_id, full_name, phone, email, is_primary_guest)
VALUES (
  '91000001-0000-0000-0000-000000000001',
  '80000001-0000-0000-0000-000000000001',
  'Lợi Trần',
  '0988776655',
  'loitn@lumora.vn',
  true
) ON CONFLICT (id) DO NOTHING;

INSERT INTO payments (id, booking_id, payment_code, provider, amount, currency, status, transaction_id, paid_at, created_at)
VALUES (
  '92000001-0000-0000-0000-000000000001',
  '80000001-0000-0000-0000-000000000001',
  'PAY-MOCK-482910',
  'MOCK',
  299000.00,
  'VND',
  'PAID',
  'TX_MOCK_882410',
  '2026-10-15 08:35:12+00',
  '2026-10-15 08:30:00+00'
) ON CONFLICT (id) DO NOTHING;

-- Booking 2: CHECKED_IN stay currently active at Đà Lạt
INSERT INTO bookings (
  id, user_id, property_id, check_in_date, check_out_date, status,
  booking_code, guest_count, subtotal, discount_amount, total_amount, currency,
  special_request, checked_in_at, check_in_qr_token, created_at
) VALUES (
  '80000001-0000-0000-0000-000000000002',
  'e0000003-0000-0000-0000-000000000001',
  '11111111-1111-1111-1111-111111111111',
  CURRENT_DATE,
  CURRENT_DATE + INTERVAL '2 days',
  'CHECKED_IN',
  'LMR-2610-4491',
  2,
  798000.00,
  0.00,
  798000.00,
  'VND',
  'Check-in sớm lúc 13:00 nếu có thể',
  NOW() - INTERVAL '3 hours',
  'LMR-CHK-4491-DL07',
  NOW() - INTERVAL '1 day'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO booking_rooms (id, booking_id, room_id, property_id, room_type_id, room_number, room_name, price_per_night, number_of_nights, room_total)
VALUES (
  '90000001-0000-0000-0000-000000000002',
  '80000001-0000-0000-0000-000000000002',
  'c1111111-0003-0000-0000-000000000003',
  '11111111-1111-1111-1111-111111111111',
  'b1111111-0002-0000-0000-000000000002',
  'No7',
  'No7 Luxury Room',
  399000.00,
  2,
  798000.00
) ON CONFLICT (id) DO NOTHING;

INSERT INTO booking_guests (id, booking_id, full_name, phone, email, is_primary_guest)
VALUES (
  '91000001-0000-0000-0000-000000000002',
  '80000001-0000-0000-0000-000000000002',
  'Lợi Trần',
  '0988776655',
  'loitn@lumora.vn',
  true
) ON CONFLICT (id) DO NOTHING;

INSERT INTO payments (id, booking_id, payment_code, provider, amount, currency, status, transaction_id, paid_at, created_at)
VALUES (
  '92000001-0000-0000-0000-000000000002',
  '80000001-0000-0000-0000-000000000002',
  'PAY-MOCK-774912',
  'MOCK',
  798000.00,
  'VND',
  'PAID',
  'TX_MOCK_449102',
  NOW() - INTERVAL '1 day',
  NOW() - INTERVAL '1 day'
) ON CONFLICT (id) DO NOTHING;

-- Booking 3: CONFIRMED upcoming stay at Sài Gòn
INSERT INTO bookings (
  id, user_id, property_id, check_in_date, check_out_date, status,
  booking_code, guest_count, subtotal, discount_amount, total_amount, currency,
  special_request, check_in_qr_token, created_at
) VALUES (
  '80000001-0000-0000-0000-000000000003',
  'e0000003-0000-0000-0000-000000000002',
  '22222222-2222-2222-2222-222222222222',
  CURRENT_DATE + INTERVAL '3 days',
  CURRENT_DATE + INTERVAL '5 days',
  'CONFIRMED',
  'LMR-2610-9102',
  2,
  698000.00,
  0.00,
  698000.00,
  'VND',
  'Phòng tầng cao thoáng đãng',
  'LMR-CHK-9102-SG02',
  NOW() - INTERVAL '6 hours'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO booking_rooms (id, booking_id, room_id, property_id, room_type_id, room_number, room_name, price_per_night, number_of_nights, room_total)
VALUES (
  '90000001-0000-0000-0000-000000000003',
  '80000001-0000-0000-0000-000000000003',
  'c2222222-0002-0000-0000-000000000002',
  '22222222-2222-2222-2222-222222222222',
  'b2222222-0002-0000-0000-000000000002',
  'No2',
  'No2 Courtyard Loft',
  349000.00,
  2,
  698000.00
) ON CONFLICT (id) DO NOTHING;

INSERT INTO booking_guests (id, booking_id, full_name, phone, email, is_primary_guest)
VALUES (
  '91000001-0000-0000-0000-000000000003',
  '80000001-0000-0000-0000-000000000003',
  'Mai Phương',
  '0912345678',
  'guest.phuong@gmail.com',
  true
) ON CONFLICT (id) DO NOTHING;

INSERT INTO payments (id, booking_id, payment_code, provider, amount, currency, status, transaction_id, paid_at, created_at)
VALUES (
  '92000001-0000-0000-0000-000000000003',
  '80000001-0000-0000-0000-000000000003',
  'PAY-MOCK-910283',
  'MOCK',
  698000.00,
  'VND',
  'PAID',
  'TX_MOCK_910283',
  NOW() - INTERVAL '5 hours',
  NOW() - INTERVAL '6 hours'
) ON CONFLICT (id) DO NOTHING;

-- Booking 4: CONFIRMED upcoming stay at Vũng Tàu
INSERT INTO bookings (
  id, user_id, property_id, check_in_date, check_out_date, status,
  booking_code, guest_count, subtotal, discount_amount, total_amount, currency,
  check_in_qr_token, created_at
) VALUES (
  '80000001-0000-0000-0000-000000000004',
  'e0000003-0000-0000-0000-000000000003',
  '33333333-3333-3333-3333-333333333333',
  CURRENT_DATE + INTERVAL '6 days',
  CURRENT_DATE + INTERVAL '8 days',
  'CONFIRMED',
  'LMR-2610-3315',
  2,
  658000.00,
  0.00,
  658000.00,
  'VND',
  'LMR-CHK-3315-VT01',
  NOW() - INTERVAL '2 days'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO booking_rooms (id, booking_id, room_id, property_id, room_type_id, room_number, room_name, price_per_night, number_of_nights, room_total)
VALUES (
  '90000001-0000-0000-0000-000000000004',
  '80000001-0000-0000-0000-000000000004',
  'c3333333-0001-0000-0000-000000000001',
  '33333333-3333-3333-3333-333333333333',
  'b3333333-0001-0000-0000-000000000001',
  'No101',
  'No101 Ocean Breeze',
  329000.00,
  2,
  658000.00
) ON CONFLICT (id) DO NOTHING;

INSERT INTO booking_guests (id, booking_id, full_name, phone, email, is_primary_guest)
VALUES (
  '91000001-0000-0000-0000-000000000004',
  '80000001-0000-0000-0000-000000000004',
  'Việt Hoàng',
  '0934567890',
  'guest.hoang@gmail.com',
  true
) ON CONFLICT (id) DO NOTHING;

INSERT INTO payments (id, booking_id, payment_code, provider, amount, currency, status, transaction_id, paid_at, created_at)
VALUES (
  '92000001-0000-0000-0000-000000000004',
  '80000001-0000-0000-0000-000000000004',
  'PAY-MOCK-331572',
  'MOCK',
  658000.00,
  'VND',
  'PAID',
  'TX_MOCK_331572',
  NOW() - INTERVAL '2 days',
  NOW() - INTERVAL '2 days'
) ON CONFLICT (id) DO NOTHING;

-- Booking 5: CANCELLED booking with REFUND at Hà Nội
INSERT INTO bookings (
  id, user_id, property_id, check_in_date, check_out_date, status,
  booking_code, guest_count, subtotal, discount_amount, total_amount, currency,
  cancelled_at, cancelled_by_user_id, cancellation_reason, created_at
) VALUES (
  '80000001-0000-0000-0000-000000000005',
  'e0000003-0000-0000-0000-000000000002',
  '44444444-4444-4444-4444-444444444444',
  CURRENT_DATE + INTERVAL '10 days',
  CURRENT_DATE + INTERVAL '12 days',
  'CANCELLED',
  'LMR-2610-6621',
  2,
  718000.00,
  0.00,
  718000.00,
  'VND',
  NOW() - INTERVAL '4 hours',
  'e0000003-0000-0000-0000-000000000002',
  'Thay đổi kế hoạch công tác',
  NOW() - INTERVAL '1 day'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO booking_rooms (id, booking_id, room_id, property_id, room_type_id, room_number, room_name, price_per_night, number_of_nights, room_total)
VALUES (
  '90000001-0000-0000-0000-000000000005',
  '80000001-0000-0000-0000-000000000005',
  'c4444444-0001-0000-0000-000000000001',
  '44444444-4444-4444-4444-444444444444',
  'b4444444-0001-0000-0000-000000000001',
  'No301',
  'No301 Heritage Room',
  359000.00,
  2,
  718000.00
) ON CONFLICT (id) DO NOTHING;

INSERT INTO booking_guests (id, booking_id, full_name, phone, email, is_primary_guest)
VALUES (
  '91000001-0000-0000-0000-000000000005',
  '80000001-0000-0000-0000-000000000005',
  'Mai Phương',
  '0912345678',
  'guest.phuong@gmail.com',
  true
) ON CONFLICT (id) DO NOTHING;

INSERT INTO payments (id, booking_id, payment_code, provider, amount, currency, status, transaction_id, paid_at, refunded_at, created_at)
VALUES (
  '92000001-0000-0000-0000-000000000005',
  '80000001-0000-0000-0000-000000000005',
  'PAY-MOCK-662199',
  'MOCK',
  718000.00,
  'VND',
  'REFUNDED',
  'TX_MOCK_662199',
  NOW() - INTERVAL '1 day',
  NOW() - INTERVAL '4 hours',
  NOW() - INTERVAL '1 day'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO refunds (
  id, booking_id, payment_id, refund_code, refund_amount, fee_amount,
  refund_percentage, currency, policy_tier, status, reason, processed_at
) VALUES (
  '93000001-0000-0000-0000-000000000001',
  '80000001-0000-0000-0000-000000000005',
  '92000001-0000-0000-0000-000000000005',
  'REF-2610-6621',
  718000.00,
  0.00,
  100,
  'VND',
  'FULL_REFUND',
  'REFUNDED',
  'Hủy trước ngày nhận phòng > 7 ngày theo chính sách miễn phí 100%',
  NOW() - INTERVAL '4 hours'
) ON CONFLICT (id) DO NOTHING;

-- Booking 6: PENDING_PAYMENT hold (15-min countdown)
INSERT INTO bookings (
  id, user_id, property_id, check_in_date, check_out_date, status,
  booking_code, guest_count, subtotal, discount_amount, total_amount, currency,
  expires_at, created_at
) VALUES (
  '80000001-0000-0000-0000-000000000006',
  'e0000003-0000-0000-0000-000000000001',
  '22222222-2222-2222-2222-222222222222',
  CURRENT_DATE + INTERVAL '4 days',
  CURRENT_DATE + INTERVAL '5 days',
  'PENDING_PAYMENT',
  'LMR-2610-1109',
  1,
  249000.00,
  0.00,
  249000.00,
  'VND',
  NOW() + INTERVAL '14 minutes',
  NOW() - INTERVAL '1 minute'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO booking_rooms (id, booking_id, room_id, property_id, room_type_id, room_number, room_name, price_per_night, number_of_nights, room_total)
VALUES (
  '90000001-0000-0000-0000-000000000006',
  '80000001-0000-0000-0000-000000000006',
  'c2222222-0001-0000-0000-000000000001',
  '22222222-2222-2222-2222-222222222222',
  'b2222222-0001-0000-0000-000000000001',
  'No1',
  'No1 Minimal Studio',
  249000.00,
  1,
  249000.00
) ON CONFLICT (id) DO NOTHING;

INSERT INTO booking_guests (id, booking_id, full_name, phone, email, is_primary_guest)
VALUES (
  '91000001-0000-0000-0000-000000000006',
  '80000001-0000-0000-0000-000000000006',
  'Lợi Trần',
  '0988776655',
  'loitn@lumora.vn',
  true
) ON CONFLICT (id) DO NOTHING;

INSERT INTO payments (id, booking_id, payment_code, provider, amount, currency, status, created_at)
VALUES (
  '92000001-0000-0000-0000-000000000006',
  '80000001-0000-0000-0000-000000000006',
  'PAY-MOCK-110944',
  'MOCK',
  249000.00,
  'VND',
  'PENDING',
  NOW() - INTERVAL '1 minute'
) ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 8. ROOM ACCESS CODES (for today, yesterday, tomorrow)
-- ============================================================================
INSERT INTO room_access_codes (id, room_id, code_value, valid_date, status)
VALUES
  -- Đà Lạt rooms
  ('e1111111-0001-0000-0000-000000000001', 'c1111111-0001-0000-0000-000000000001', '482910', CURRENT_DATE, 'ACTIVE'),
  ('e1111111-0002-0000-0000-000000000001', 'c1111111-0002-0000-0000-000000000002', '593821', CURRENT_DATE, 'ACTIVE'),
  ('e1111111-0003-0000-0000-000000000001', 'c1111111-0003-0000-0000-000000000003', '619283', CURRENT_DATE, 'ACTIVE'),
  ('e1111111-0004-0000-0000-000000000001', 'c1111111-0004-0000-0000-000000000004', '772914', CURRENT_DATE, 'ACTIVE'),
  -- Sài Gòn rooms
  ('e2222222-0001-0000-0000-000000000001', 'c2222222-0001-0000-0000-000000000001', '109283', CURRENT_DATE, 'ACTIVE'),
  ('e2222222-0002-0000-0000-000000000001', 'c2222222-0002-0000-0000-000000000002', '293847', CURRENT_DATE, 'ACTIVE'),
  ('e2222222-0003-0000-0000-000000000001', 'c2222222-0003-0000-0000-000000000003', '384920', CURRENT_DATE, 'ACTIVE'),
  -- Vũng Tàu rooms
  ('e3333333-0001-0000-0000-000000000001', 'c3333333-0001-0000-0000-000000000001', '849201', CURRENT_DATE, 'ACTIVE'),
  ('e3333333-0002-0000-0000-000000000001', 'c3333333-0002-0000-0000-000000000002', '920184', CURRENT_DATE, 'ACTIVE'),
  -- Hà Nội rooms
  ('e4444444-0001-0000-0000-000000000001', 'c4444444-0001-0000-0000-000000000001', '671928', CURRENT_DATE, 'ACTIVE'),
  ('e4444444-0002-0000-0000-000000000001', 'c4444444-0002-0000-0000-000000000002', '782910', CURRENT_DATE, 'ACTIVE'),
  ('e4444444-0003-0000-0000-000000000001', 'c4444444-0003-0000-0000-000000000003', '893019', CURRENT_DATE, 'ACTIVE')
ON CONFLICT (room_id, valid_date) DO NOTHING;

-- ============================================================================
-- 9. AUDIT LOGS
-- ============================================================================
INSERT INTO audit_logs (id, actor_user_id, action, entity_type, entity_id, metadata, created_at)
VALUES
  (
    'a1000001-0000-0000-0000-000000000001',
    'e0000001-0000-0000-0000-000000000001',
    'ASSIGN_MANAGER_PROPERTY',
    'user_properties',
    'e0000002-0000-0000-0000-000000000001',
    '{"propertyId":"11111111-1111-1111-1111-111111111111","managerEmail":"manager.dalat@lumora.vn"}',
    NOW() - INTERVAL '10 days'
  ),
  (
    'a1000001-0000-0000-0000-000000000002',
    'e0000001-0000-0000-0000-000000000001',
    'ADMIN_BLOCK_ROOM',
    'rooms',
    'c1111111-0004-0000-0000-000000000004',
    '{"roomNumber":"No8","reason":"Bảo dưỡng máy sưởi và sơn lại tường"}',
    NOW() - INTERVAL '3 days'
  ),
  (
    'a1000001-0000-0000-0000-000000000003',
    'e0000002-0000-0000-0000-000000000001',
    'GUEST_CHECK_IN',
    'bookings',
    '80000001-0000-0000-0000-000000000002',
    '{"bookingCode":"LMR-2610-4491","roomNumber":"No7","checkedInBy":"manager.dalat@lumora.vn"}',
    NOW() - INTERVAL '3 hours'
  )
ON CONFLICT (id) DO NOTHING;
