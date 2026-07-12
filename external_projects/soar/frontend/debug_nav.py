import time
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

opts = Options()
opts.add_argument("--headless=new")
opts.add_argument("--no-sandbox")
opts.add_argument("--disable-dev-shm-usage")
driver = webdriver.Chrome(options=opts)
wait = WebDriverWait(driver, 15)

driver.get("http://localhost:3130/login")
wait.until(EC.presence_of_element_located((By.ID, "email"))).send_keys("igor@soar.test")
driver.find_element(By.ID, "password").send_keys("password")
driver.find_element(By.CSS_SELECTOR, "button[type=submit]").click()
wait.until(EC.presence_of_element_located((By.TAG_NAME, "aside")))

els = driver.find_elements(By.XPATH, "//aside//span[text()='Metas do Q3']")
print("spans 'Metas do Q3' visíveis:", len(els), [e.is_displayed() for e in els])
els[0].click()
time.sleep(2)
print("url:", driver.current_url)
inputs = driver.find_elements(By.CSS_SELECTOR, "main input")
print("main input value:", inputs[0].get_attribute("value") if inputs else None)
print("main text:", driver.find_element(By.TAG_NAME, "main").text[:150].replace("\n", " | "))
driver.quit()
